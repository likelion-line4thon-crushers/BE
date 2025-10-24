package line4thon.boini.presenter.room.service;

import line4thon.boini.global.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import line4thon.boini.global.common.exception.CustomException;
import line4thon.boini.global.common.exception.GlobalErrorCode;
import line4thon.boini.global.jwt.service.JwtService;
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
    srt.opsForValue().set(presenterKeyHashKey(roomId), hash, Duration.ofSeconds(ttlSec));
    log.debug("PresenterKey hash stored roomId={} ttl={}s", roomId, ttlSec);
    return key;
  }

  // presenterKey 검증 (제공받은 원문을 해시해 비교)
  public boolean verifyPresenterKey(String roomId, String providedKey) {
    String expect = srt.opsForValue().get(presenterKeyHashKey(roomId));
    if (expect == null) {
      log.warn("PresenterKey hash not found (expired?) roomId={}", roomId);
      return false;
    }
    return slowEquals(expect, sha256Hex(providedKey));
  }

  // 발표자용 JWT 발급 (role=presenter).
  public String issuePresenterToken(String roomId) {
    long hours = props.getJwt().getPresenterTtlHours();
    return jwt.issueJoinToken(roomId, "presenter", java.time.Duration.ofHours(hours));
  }

  // presenterKey 검증 후 발표자 JWT 재발급. 실패 시 UNAUTHORIZED.
  public String refreshPresenterToken(String roomId, String presenterKey) {
    if (!verifyPresenterKey(roomId, presenterKey)) {
      throw new CustomException(GlobalErrorCode.UNAUTHORIZED);
    }
    return issuePresenterToken(roomId);
  }

  // 재발급 성공 시 키 회전: 새 키를 발급하고 해시를 교체하여 보안 강화
  public String rotatePresenterKey(String roomId) {
    String newKey  = generatePresenterKey();
    String newHash = sha256Hex(newKey);
    long ttlSec = props.getRoom().getTtlSeconds();
    srt.opsForValue().set(presenterKeyHashKey(roomId), newHash, java.time.Duration.ofSeconds(ttlSec));
    log.info("PresenterKey rotated roomId={}", roomId);
    return newKey;
  }

  private String presenterKeyHashKey(String roomId) {
    return "room:" + roomId + ":presenterKeyHash";
  }

  // 충분히 긴 랜덤 키(원문). UI/네트워크에 안전히 실어 나르기 쉬운 형태.
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
      throw new RuntimeException("Failed to hash presenterKey", e);
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
}
