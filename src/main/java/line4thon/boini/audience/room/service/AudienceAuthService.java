package line4thon.boini.audience.room.service;

import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import line4thon.boini.global.config.AppProperties;
import line4thon.boini.global.jwt.service.JwtService;

@Service
@RequiredArgsConstructor
public class AudienceAuthService {

  private static final String ROLE_AUDIENCE = "audience";

  private final JwtService jwtService;
  private final AppProperties props; // TTL 설정 주입

  // 청중 토큰 발급
  public IssuedAudience issueAudienceToken(String roomId) {
    // 클라이언트 식별용 익명 ID(토큰에는 포함되지 않음)
    String audienceId = UUID.randomUUID().toString();

    // JwtService 시그니처에 맞춰 발급 (roomId, role, ttl)
    long ttlSec = props.getRoom().getTtlSeconds();                     // 운영 정책에 따라 조정 가능
    String token = jwtService.issueJoinToken(roomId, ROLE_AUDIENCE,    // role=audience
        Duration.ofSeconds(ttlSec));

    return new IssuedAudience(audienceId, token);
  }

  public record IssuedAudience(String audienceId, String audienceToken) {}
}
