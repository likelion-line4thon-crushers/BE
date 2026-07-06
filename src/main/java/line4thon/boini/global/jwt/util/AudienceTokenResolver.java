package line4thon.boini.global.jwt.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.HttpServletRequest;
import line4thon.boini.global.common.exception.CustomException;
import line4thon.boini.global.jwt.exception.JwtErrorCode;
import line4thon.boini.global.jwt.service.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 청중용 REST 요청에서 Authorization: Bearer 토큰을 검증하고 청중 신원을 추출한다.
 *
 * <p>토큰은 입장(join) 시 서버가 서명해 발급한 것이므로, 유효한 서명은 곧 "이 방에 실제로
 * 입장한 청중"이라는 증명이 된다. 따라서 audienceId 를 요청 본문이 아니라 토큰의 subject 에서
 * 가져오면 위조/무단 제출을 원천 차단할 수 있다.
 */
@Component
@RequiredArgsConstructor
public class AudienceTokenResolver {

  private static final String ROLE_AUDIENCE = "audience";

  private final JwtService jwtService;

  /**
   * 요청의 Bearer 토큰을 검증하고, 경로의 roomId 와 일치하는 방의 청중이면 audienceId 를 반환한다.
   *
   * @param request     현재 HTTP 요청 (Authorization 헤더에서 토큰을 읽는다)
   * @param expectedRoomId 경로 변수로 전달된 roomId (토큰의 roomId 클레임과 일치해야 한다)
   * @return 토큰 subject 에 담긴 audienceId
   */
  public String resolveAudienceId(HttpServletRequest request, String expectedRoomId) {
    String token = extractBearerToken(request);

    Claims claims;
    try {
      claims = jwtService.parse(token);
    } catch (ExpiredJwtException e) {
      throw new CustomException(JwtErrorCode.JWT_EXPIRED);
    } catch (Exception e) {
      throw new CustomException(JwtErrorCode.JWT_INVALID);
    }

    String role = claims.get("role", String.class);
    String roomId = claims.get("roomId", String.class);
    String audienceId = claims.getSubject();

    if (role == null || roomId == null || audienceId == null || audienceId.isBlank()) {
      throw new CustomException(JwtErrorCode.JWT_CLAIM_INVALID);
    }
    if (!ROLE_AUDIENCE.equalsIgnoreCase(role)) {
      throw new CustomException(JwtErrorCode.JWT_CLAIM_INVALID);
    }
    // 토큰이 발급된 방과 요청 대상 방이 다르면 다른 방의 신원으로 위장한 것이다.
    if (expectedRoomId == null || !roomId.equals(expectedRoomId)) {
      throw new CustomException(JwtErrorCode.JWT_CLAIM_INVALID);
    }

    return audienceId;
  }

  private String extractBearerToken(HttpServletRequest request) {
    String header = request.getHeader("Authorization");
    if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
      throw new CustomException(JwtErrorCode.JWT_MISSING);
    }
    String token = header.substring(7).trim();
    if (token.isEmpty()) {
      throw new CustomException(JwtErrorCode.JWT_MISSING);
    }
    return token;
  }
}
