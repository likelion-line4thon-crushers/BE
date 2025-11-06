//package line4thon.boini.global.jwt.util;
//
//import io.jsonwebtoken.Claims;
//import jakarta.servlet.http.HttpServletRequest;
//import lombok.RequiredArgsConstructor;
//import org.springframework.security.core.Authentication;
//import org.springframework.stereotype.Component;
//import line4thon.boini.global.jwt.service.JwtService;
//
//@Component
//@RequiredArgsConstructor
//public class JwtAudienceResolver {
//
//  private final JwtService jwtService;
//
//  public String extractAudienceId(String token) {
//    Claims claims = jwtService.parse(token);
//    return claims.getId();
//  }
//
//  public String extractRoomId(String token) {
//    Claims claims = jwtService.parse(token);
//    return claims.get("roomId", String.class);
//  }
//
//  public String resolveToken(Authentication auth, HttpServletRequest request) {
//    // Authentication 기반 (JWT 필터가 세팅된 경우)
//    if (auth != null && auth.getCredentials() instanceof String creds && !creds.isBlank()) {
//      return creds;
//    }
//
//    // Authorization 헤더 기반 (Bearer)
//    String header = request.getHeader("Authorization");
//    if (header != null && header.startsWith("Bearer ")) {
//      return header.substring(7);
//    }
//
//    // 없을 경우 예외
//    // throw new CustomException(JwtErrorCode.JWT_MISSING);
//    throw new IllegalStateException("JWT 토큰이 없습니다.");
//  }
//}