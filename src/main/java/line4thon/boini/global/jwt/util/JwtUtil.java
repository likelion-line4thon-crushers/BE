package line4thon.boini.global.jwt.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class JwtUtil {

    public String resolveToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7); // "Bearer " 제거
        }

        return null;
    }

    // 나머지 jwt 검증 관련 메서드들...
    public boolean validateToken(String token) {
        // JWT 검증 로직 작성
        return true;
    }

    public Authentication getAuthentication(String token) {
        // JWT에서 Authentication 객체 추출
        return null;
    }
}
