package line4thon.boini.global.websocket;

import java.util.Map;
import java.util.regex.Pattern;

import line4thon.boini.global.jwt.service.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

  private static final Pattern SOCKJS_SESSION_PATH =
      Pattern.compile(".*/\\w+/[\\w.-]+/(websocket|xhr|xhr_streaming|eventsource|jsonp).*");

  private final JwtService jwt;

  /**
   * 웹소켓 핸드셰이크 직전에 호출되는 인터셉터.
   * - SockJS 보조 경로는 인증 없이 통과
   * - 실제 WS 업그레이드 요청이면 Authorization 헤더의 Bearer 토큰을 확인/파싱
   * - role, roomId, jwt 등을 attrs에 기록하여 이후 단계(ChannelInterceptor)에서 사용
   */
  @Override
  public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
      WebSocketHandler wsHandler, Map<String, Object> attrs) {

    var uri = request.getURI();
    var path = uri.getPath();
    HttpHeaders headers = request.getHeaders();

    log.info("[핸드셰이크] 요청 URI = {}", uri);

    // 0) SockJS 보조 엔드포인트는 인증 없이 통과
    if (isSockJsAux(path)) {
      log.debug("[핸드셰이크] SockJS 보조 경로 -> 인증 없이 통과: {}", path);
      return true;
    }

    // 1) 실제 WebSocket 업그레이드 요청인지 확인 (Upgrade: websocket)
    String upgrade = headers.getUpgrade(); // Spring 6+: null 또는 "websocket"
    boolean isWsUpgrade = "websocket".equalsIgnoreCase(upgrade);
    if (!isWsUpgrade) {
      log.debug("[핸드셰이크] WebSocket 업그레이드가 아님. 요청 거절.");
      return false;
    }

    // 2) 토큰 추출: Authorization 헤더만 허용
    String token = extractBearer(headers.getFirst(HttpHeaders.AUTHORIZATION));

    if (token == null || token.isBlank()) {
      log.warn("[핸드셰이크] 토큰이 없습니다. (Authorization: Bearer ... 필요)");
      return true;
    }

    // 3) 토큰 파싱 및 엔드포인트-역할 일치 검사
    try {
      var claims = jwt.parse(token);
      String role = String.valueOf(claims.get("role"));
      String roomId = String.valueOf(claims.get("roomId"));

      // 발표자/청중 엔드포인트 라우팅 체크(있는 경우만)
      if (path.startsWith("/ws/presenter") && !"presenter".equals(role)) {
        log.warn("[핸드셰이크] 발표자 엔드포인트에 비발표자 역할 접근. role={}", role);
        return false;
      }
      if (path.startsWith("/ws/audience") && !"audience".equals(role)) {
        log.warn("[핸드셰이크] 청중 엔드포인트에 비청중 역할 접근. role={}", role);
        return false;
      }

      attrs.put("role", role);
      attrs.put("roomId", roomId);
      attrs.put("jwt", token);
      log.debug("[핸드셰이크] 토큰 검증 및 속성 설정 완료. role={} roomId={}", role, roomId);
    } catch (Exception e) {
      log.warn("[핸드셰이크] 토큰 파싱/검증 실패: {}", e.getMessage());
      return false;
    }

    return true;
  }

  @Override
  public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
      WebSocketHandler wsHandler, Exception exception) {}

  // Authorization 헤더에서 Bearer 토큰을 추출.
  private String extractBearer(String authHeader) {
    if (authHeader == null)
      return null;
    if (authHeader.startsWith("Bearer "))
      return authHeader.substring(7);

    return null;
  }

   // SockJS 보조 경로 여부 판단.
  private boolean isSockJsAux(String path) {
    return path.endsWith("/info")
        || path.contains("/iframe.html")
        || SOCKJS_SESSION_PATH.matcher(path).matches();
  }
}
