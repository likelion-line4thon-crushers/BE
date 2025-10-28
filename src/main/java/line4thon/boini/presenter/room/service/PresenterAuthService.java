package line4thon.boini.presenter.room.service;

import line4thon.boini.global.config.AppProperties;
import line4thon.boini.presenter.room.exception.PresenterErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import line4thon.boini.global.common.exception.CustomException;
import line4thon.boini.global.common.exception.GlobalErrorCode;
import line4thon.boini.global.jwt.service.JwtService;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PresenterAuthService {

  private final StringRedisTemplate srt;
  private final JwtService jwt;
  private final AppProperties props;

  // 발표자 재발급 키(원문) 생성 & 해시를 Redis에 저장. (원문은 응답으로만 반환; 서버에 평문 저장 X)
  public String generateAndStorePresenterKey(String roomId) {
    String key = generatePresenterKey();
    String hash = sha256Hex(key);
    long ttlSec = props.getRoom().getTtlSeconds();
    try {                                                                  // [FIX] Redis 예외 래핑
      srt.opsForValue().set(presenterKeyHashKey(roomId), hash, Duration.ofSeconds(ttlSec));
    } catch (DataAccessException e) {
      log.error("발표자 키 해시 저장 실패: roomId={}, err={}", roomId, e.toString());
      throw new CustomException(PresenterErrorCode.REDIS_ERROR);
    }
    log.debug("발표자 키 해시 저장 완료: roomId={} / TTL={}초", roomId, ttlSec);
    return key;
  }

  // presenterKey 검증 (제공받은 원문을 해시해 비교)
  public boolean verifyPresenterKey(String roomId, String providedKey) {
    validateRoomId(roomId);                                                // [FIX]
    if (providedKey == null || providedKey.isBlank()) {                    // [FIX] 입력값 검증
      log.warn("발표자 키가 비어있음: roomId={}", roomId);
      return false;
    }
    String expect;
    try {                                                                  // [FIX] Redis 예외 래핑
      expect = srt.opsForValue().get(presenterKeyHashKey(roomId));
    } catch (DataAccessException e) {
      log.error("발표자 키 해시 조회 실패: roomId={}, err={}", roomId, e.toString());
      throw new CustomException(PresenterErrorCode.REDIS_ERROR);
    }

    if (expect == null) {
      log.warn("발표자 키 해시가 존재하지 않거나 만료됨: roomId={}", roomId);
      return false;
    }
    boolean match = slowEquals(expect, sha256Hex(providedKey));
    if (!match) {
      log.warn("발표자 키 불일치: roomId={}", roomId);
    } else {
      log.info("발표자 키 검증 성공: roomId={}", roomId);
    }
    return match;
  }

  // 발표자용 JWT 발급 (role=presenter).
  public String issuePresenterToken(String roomId) {
    validateRoomId(roomId);                                                // [FIX]
    long hours = props.getJwt().getPresenterTtlHours();
    try {                                                                  // [FIX] 발급 실패를 명확한 코드로 래핑
      String token = jwt.issueJoinToken(roomId, "presenter", Duration.ofHours(hours));
      log.info("발표자용 JWT 발급 완료: roomId={} / 유효시간={}시간", roomId, hours);
      return token;
    } catch (Exception e) {
      log.error("발표자용 JWT 발급 실패: roomId={}, err={}", roomId, e.toString());
      throw new CustomException(PresenterErrorCode.JWT_ISSUE_FAILED);
    }
  }

  // presenterKey 검증 후 발표자 JWT 재발급. 실패 시 UNAUTHORIZED.
  public String refreshPresenterToken(String roomId, String presenterKey) {
    // 하위호환 유지: 내부적으로 회전 없이 재발급만 수행하도록 신규 메서드 위임
    RefreshResult res = refreshTokenAndMaybeRotate(roomId, presenterKey, false); // [ADDED]
    return res.token();
  }

  // presenterKey 검증 후, (옵션) 키 회전까지 수행하고 토큰을 반환
  // rotate=true 이면 새 presenterKey도 함께 반환된다.
  public RefreshResult refreshTokenAndMaybeRotate(String roomId, String presenterKey, boolean rotate) {
    if (!verifyPresenterKey(roomId, presenterKey)) {
      throw new CustomException(GlobalErrorCode.UNAUTHORIZED);
    }

    String token = issuePresenterToken(roomId);
    String rotated = null;

    if (rotate) {
      rotated = rotatePresenterKey(roomId);
      log.info("발표자 토큰 재발급 + 키 회전 완료: roomId={}", roomId);
    } else {
      log.info("발표자 토큰 재발급 완료(회전 없음): roomId={}", roomId);
    }

    return new RefreshResult(token, rotated);
  }

  // 재발급 성공 시 키 회전: 새 키를 발급하고 해시를 교체하여 보안 강화
  public String rotatePresenterKey(String roomId) {
    String newKey  = generatePresenterKey();
    String newHash = sha256Hex(newKey);
    long ttlSec = props.getRoom().getTtlSeconds();
    srt.opsForValue().set(presenterKeyHashKey(roomId), newHash, java.time.Duration.ofSeconds(ttlSec));
    log.info("발표자 키 회전 완료: roomId={} / TTL={}초", roomId, ttlSec);
    return newKey;
  }

  // 토큰 + (선택적) 회전된 presenterKey를 담는 응답 레코드
  public record RefreshResult(String token, String rotatedPresenterKey) {}

  // Redis 키 이름 생성 (room:<roomId>:presenterKeyHash)
  private String presenterKeyHashKey(String roomId) {
    return "room:" + roomId + ":presenterKeyHash";
  }

  // 충분히 긴 랜덤 키(원문).
  private String generatePresenterKey() {
    // UUID 2개를 붙여 64 hex 길이(하이픈 제거). 필요하면 더 길게 해도 OK.
    return UUID.randomUUID().toString().replace("-", "")
        + UUID.randomUUID().toString().replace("-", "");
  }

  // SHA-256 해시(hex 문자열)
  private String sha256Hex(String s) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(d.length * 2);
      for (byte b : d) sb.append(String.format("%02x", b));
      return sb.toString();
    } catch (Exception e) {
      throw new RuntimeException("발표자 키 해시 처리 중 오류가 발생했습니다.", e);
    }
  }

  // 타이밍 공격 완화용 상수시간 비교
  private boolean slowEquals(String a, String b) {
    if (a == null || b == null) return false;
    int r = 0, n = Math.max(a.length(), b.length());
    for (int i = 0; i < n; i++) {
      char ca = i < a.length() ? a.charAt(i) : 0;
      char cb = i < b.length() ? b.charAt(i) : 0;
      r |= (ca ^ cb);
    }
    return r == 0;
  }

  private void validateRoomId(String roomId) {
    if (roomId == null || roomId.isBlank()) {
      log.warn("유효하지 않은 roomId 입력: {}", roomId);
      throw new CustomException(PresenterErrorCode.INVALID_ROOM_ID);
    }
  }
}
