package line4thon.boini.global.jwt.util;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter implements Filter {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public void doFilter(ServletRequest servletRequest,
                         ServletResponse servletResponse,
                         FilterChain filterChain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;

        // Authorization 헤더에서 토큰 추출
        String token = jwtUtil.resolveToken(request);

        // 요청 객체에 토큰 저장 (필요할 때 가져다 쓰기용)
        request.setAttribute("token", token);

        // 유효한 토큰이면 인증정보 설정
        if (token != null && jwtUtil.validateToken(token)) {
            Authentication auth = jwtUtil.getAuthentication(token);
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, servletResponse);
    }
}
