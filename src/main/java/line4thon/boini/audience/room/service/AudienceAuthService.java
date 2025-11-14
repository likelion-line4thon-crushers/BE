package line4thon.boini.audience.room.service;

import java.time.Duration;
import java.util.UUID;

import line4thon.boini.audience.room.dto.response.JoinResponse;
import line4thon.boini.audience.room.dto.response.RoomInfoResponse;
import line4thon.boini.global.common.exception.CustomException;
import line4thon.boini.global.common.response.BaseResponse;
import line4thon.boini.global.jwt.exception.JwtErrorCode;
import line4thon.boini.presenter.aiReport.exception.ReportErrorCode;
import line4thon.boini.presenter.room.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import line4thon.boini.global.config.AppProperties;
import line4thon.boini.global.jwt.service.JwtService;

@Service
@RequiredArgsConstructor
public class AudienceAuthService {

  private static final String ROLE_AUDIENCE = "audience";

  private final JwtService jwtService;
  private final AppProperties props;

  public IssuedAudience issueAudienceToken(String roomId) {
    try {
      String audienceId = UUID.randomUUID().toString();
      long ttlSec = props.getRoom().getTtlSeconds();

      String token = jwtService.issueJoinToken(
          roomId, ROLE_AUDIENCE, Duration.ofSeconds(ttlSec));

      return new IssuedAudience(audienceId, token);

    } catch (Exception e) {
      throw new CustomException(JwtErrorCode.JWT_UNKNOWN);
    }
  }
  public record IssuedAudience(String audienceId, String audienceToken) {}



}
