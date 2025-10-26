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

  // 재시도 횟수 (충돌 시 새로운 코드 재생성)
  private static final int MAX_RETRY = 8;

  // 코드 길이
  private static final int CODE_LENGTH = 6;

  // 코드 상태 값
  private static final String RESERVED  = "RESERVED";
  private static final String CONFIRMED = "CONFIRMED";

  private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
  private static final SecureRandom RNG = new SecureRandom();

  // 고유 코드 예약: 충돌 시 재시도하여 (code, token) 반환.
  // token 은 예약/확정/해제 시 검증용으로만 사용.
  public CodeReservation reserveUniqueCode(String roomId) {
    long ttlSec = props.getRoom().getTtlSeconds();
    for (int i = 0; i < MAX_RETRY; i++) {
      String code  = generateCode();
      String token = UUID.randomUUID().toString();

      String codeKey = codeKey(code);
      String val = encodeReservedValue(roomId, token);

      Boolean ok = srt.opsForValue().setIfAbsent(codeKey, val, Duration.ofSeconds(ttlSec));
      if (Boolean.TRUE.equals(ok)) {
        // roomId -> code 역매핑도 TTL 걸어둠 (선택)
        srt.opsForValue().set(roomCodeKey(roomId), code, Duration.ofSeconds(ttlSec));
        log.debug("code reserved code={} roomId={} ttl={}s", code, roomId, ttlSec);
        return new CodeReservation(code, token);
      }
    }
    throw new CustomException(GlobalErrorCode.INTERNAL_SERVER_ERROR);
  }

  // 예약된 코드를 확정 상태로 전환하고 TTL을 연장/갱신한다.
  public void confirmMapping(CodeReservation reservation, String roomId) {
    Objects.requireNonNull(reservation, "reservation 값은 null일 수 없습니다.");
    String code    = reservation.code();
    String token   = reservation.token();
    long ttlSec    = props.getRoom().getTtlSeconds();
    String codeKey = codeKey(code);

    String cur = srt.opsForValue().get(codeKey);
    if (cur == null) {
      log.warn("confirm failed: code not found (expired?) code={} roomId={}", code, roomId);
      throw new CustomException(GlobalErrorCode.RESOURCE_NOT_FOUND);
    }

    ReservedState rs = decodeReservedValue(cur);
    if (rs == null || !RESERVED.equals(rs.state) || !roomId.equals(rs.roomId) || !token.equals(rs.token)) {
      log.warn("confirm failed: invalid state/token code={} roomId={} cur={}", code, roomId, cur);
      throw new CustomException(GlobalErrorCode.UNAUTHORIZED);
    }

    // CONFIRMED:<roomId>
    String confirmedVal = encodeConfirmedValue(roomId);
    srt.opsForValue().set(codeKey, confirmedVal, Duration.ofSeconds(ttlSec));
    srt.opsForValue().set(roomCodeKey(roomId), code, Duration.ofSeconds(ttlSec));
    log.info("code confirmed code={} roomId={} ttl={}s", code, roomId, ttlSec);
  }

  // 예약 해제
  public void release(CodeReservation reservation) {
    if (reservation == null) return;
    String code    = reservation.code();
    String token   = reservation.token();
    String codeKey = codeKey(code);

    String cur = srt.opsForValue().get(codeKey);
    ReservedState rs = decodeReservedValue(cur);
    if (rs != null && RESERVED.equals(rs.state) && token.equals(rs.token)) {
      srt.delete(codeKey);
      log.info("code released code={} roomId={}", code, rs.roomId);
    }
  }

  // 발표 종료 시, 코드 TTL 을 짧게(1시간) 줄여 빠른 만료 유도
  public void shortenTtlAfterEnd(String roomId) {
    String code = getCodeByRoom(roomId);
    if (code == null) return;

    Duration grace = Duration.ofHours(1);
    byte[] key = codeKey(getCodeByRoom(roomId)).getBytes();
    srt.getRequiredConnectionFactory().getConnection().keyCommands().expire(key, grace);
  }

  // roomId -> code 조회(없으면 null)
  public String getCodeByRoom(String roomId) {
    return srt.opsForValue().get(roomCodeKey(roomId));
  }

  // ====== 내부 구현 ======

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
    // RESERVED:<roomId>:<token>
    if (cur == null || !cur.startsWith(RESERVED + ":")) return null;
    String[] parts = cur.split(":", 3);
    if (parts.length != 3) return null;
    return new ReservedState(parts[0], parts[1], parts[2]); // state, roomId, token
  }

  // SecureRandom 기반 코드 생성
  private String generateCode() {
    StringBuilder sb = new StringBuilder(CODE_LENGTH);
    for (int i = 0; i < CODE_LENGTH; i++) {
      int idx = RNG.nextInt(ALPHABET.length());
      sb.append(ALPHABET.charAt(idx));
    }
    return sb.toString();
  }

  private record ReservedState(String state, String roomId, String token) {}
  public record CodeReservation(String code, String token) {}
}
