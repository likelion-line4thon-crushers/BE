package line4thon.boini.presenter.room.service;

import line4thon.boini.global.common.exception.CustomException;
import line4thon.boini.global.common.exception.GlobalErrorCode;
import line4thon.boini.global.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CodeService {

  private final StringRedisTemplate srt;
  private final AppProperties props;

  private static final int MAX_RETRY = 8;

  private static final int CODE_LENGTH = 6;

  private static final String RESERVED  = "RESERVED";
  private static final String CONFIRMED = "CONFIRMED";

  private static final SecureRandom RNG = new SecureRandom();

  public CodeReservation reserveUniqueCode(String roomId) {
    long ttlSec = props.getRoom().getTtlSeconds();
    for (int i = 0; i < MAX_RETRY; i++) {
      String code  = generateCode();
      String token = UUID.randomUUID().toString();

      String codeKey = codeKey(code);
      String val = encodeReservedValue(roomId, token);

      Boolean ok = srt.opsForValue().setIfAbsent(codeKey, val, Duration.ofSeconds(ttlSec));
      if (Boolean.TRUE.equals(ok)) {
        srt.opsForValue().set(roomCodeKey(roomId), code, Duration.ofSeconds(ttlSec));
        log.debug("코드 예약 완료: code={} / roomId={} / TTL={}초", code, roomId, ttlSec);
        return new CodeReservation(code, token);
      }
    }
    log.error("코드 예약 실패: 충돌이 너무 많음");
    throw new CustomException(GlobalErrorCode.INTERNAL_SERVER_ERROR);
  }

  public void confirmMapping(CodeReservation reservation, String roomId) {
    Objects.requireNonNull(reservation, "reservation 값은 null일 수 없습니다.");
    String code    = reservation.code();
    String token   = reservation.token();
    long ttlSec    = props.getRoom().getTtlSeconds();
    String codeKey = codeKey(code);

    String cur = srt.opsForValue().get(codeKey);
    if (cur == null) {
      log.warn("코드 확정 실패: 존재하지 않거나 만료됨 (code={}, roomId={})", code, roomId);
      throw new CustomException(GlobalErrorCode.RESOURCE_NOT_FOUND);
    }

    ReservedState rs = decodeReservedValue(cur);
    if (rs == null || !RESERVED.equals(rs.state) || !roomId.equals(rs.roomId) || !token.equals(rs.token)) {
      log.warn("코드 확정 실패: 상태 또는 토큰 불일치 (code={}, roomId={}, cur={})", code, roomId, cur);
      throw new CustomException(GlobalErrorCode.UNAUTHORIZED);
    }

    String confirmedVal = encodeConfirmedValue(roomId);
    srt.opsForValue().set(codeKey, confirmedVal, Duration.ofSeconds(ttlSec));
    srt.opsForValue().set(roomCodeKey(roomId), code, Duration.ofSeconds(ttlSec));
    log.info("코드 확정 완료: code={} / roomId={} / TTL={}초", code, roomId, ttlSec);
  }

  public void release(CodeReservation reservation) {
    if (reservation == null) return;
    String code    = reservation.code();
    String token   = reservation.token();
    String codeKey = codeKey(code);

    String cur = srt.opsForValue().get(codeKey);
    ReservedState rs = decodeReservedValue(cur);
    if (rs != null && RESERVED.equals(rs.state) && token.equals(rs.token)) {
      srt.delete(codeKey);
      log.info("🗑코드 해제 완료: code={} / roomId={}", code, rs.roomId);
    }
  }

  public void shortenTtlAfterEnd(String roomId) {
    String code = getCodeByRoom(roomId);
    if (code == null) return;

    Duration grace = Duration.ofHours(1);
    byte[] key = codeKey(getCodeByRoom(roomId)).getBytes();
    srt.getRequiredConnectionFactory().getConnection().keyCommands().expire(key, grace);
  }

  public String getCodeByRoom(String roomId) {
    return srt.opsForValue().get(roomCodeKey(roomId));
  }

  public String resolveRoomIdByCodeOrThrow(String code) {
    String val = srt.opsForValue().get(codeKey(code));
    if (val == null) {
      log.warn("코드 조회 실패: 존재하지 않음 또는 만료됨 code={}", code);
      throw new CustomException(GlobalErrorCode.RESOURCE_NOT_FOUND); // 코드 만료/없음
    }
    if (val.startsWith(CONFIRMED + ":")) {
      return val.substring((CONFIRMED + ":").length());
    }
    if (val.startsWith(RESERVED + ":")) {
      log.warn("코드 조회 거부: 아직 확정되지 않음 code={}", code);
      throw new CustomException(GlobalErrorCode.UNAUTHORIZED);
    }
    log.error("코드 상태 알 수 없음 code={} value={}", code, val);
    throw new CustomException(GlobalErrorCode.UNAUTHORIZED);
  }

  private String codeKey(String code) {
    return "code:" + code;
  }

  private String roomCodeKey(String roomId) {
    return "room:" + roomId + ":code";
  }

  private String encodeReservedValue(String roomId, String token) {
    return RESERVED + ":" + roomId + ":" + token;
  }

  private String encodeConfirmedValue(String roomId) {
    return CONFIRMED + ":" + roomId;
  }

  private ReservedState decodeReservedValue(String cur) {
    if (cur == null || !cur.startsWith(RESERVED + ":")) return null;
    String[] parts = cur.split(":", 3);
    if (parts.length != 3) return null;
    return new ReservedState(parts[0], parts[1], parts[2]);
  }

  private String generateCode() {
    int min = (int) Math.pow(10, CODE_LENGTH - 1);
    int max = (int) Math.pow(10, CODE_LENGTH) - 1;
    int number = RNG.nextInt((max - min) + 1) + min;
    return String.valueOf(number);
  }

  private record ReservedState(String state, String roomId, String token) {}

  public record CodeReservation(String code, String token) {}
}
