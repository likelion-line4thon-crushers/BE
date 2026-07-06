package line4thon.boini.global.websocket;

import io.jsonwebtoken.Claims;
import line4thon.boini.global.jwt.service.JwtService;
import line4thon.boini.presenter.room.service.RoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * WebSocket 연결 생명주기로 청중 온라인 상태(presence)를 관리한다.
 *  - CONNECT    : CONNECT 프레임의 Authorization 헤더(JWT)로 청중을 등록한다.
 *                 (STOMP ChannelInterceptor 가 inboundChannel 에 등록돼 있지 않아
 *                  세션 속성을 신뢰할 수 없으므로 토큰을 직접 파싱한다.)
 *  - DISCONNECT : wsSessionId 로 청중 신원을 복원해 해제한다(탭 닫기/새로고침/네트워크 끊김).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class WebSocketEventListener {

  private final RoomService roomService;
  private final JwtService jwtService;

  @EventListener
  public void handleSessionConnect(SessionConnectEvent event) {
    StompHeaderAccessor acc = StompHeaderAccessor.wrap(event.getMessage());
    String wsSessionId = acc.getSessionId();

    String authz = firstNative(acc, "Authorization");
    if (authz == null) {
      authz = firstNative(acc, "authorization");
    }
    if (authz == null || !authz.toLowerCase().startsWith("bearer ")) {
      log.debug("[WebSocket] CONNECT 토큰 없음: sessionId={}", wsSessionId);
      return;
    }

    String role;
    String roomId;
    String audienceId;
    try {
      Claims claims = jwtService.parse(authz.substring("bearer ".length()).trim());
      role = claims.get("role", String.class);
      roomId = claims.get("roomId", String.class);
      audienceId = claims.getSubject();
    } catch (Exception e) {
      log.warn("[WebSocket] CONNECT 토큰 파싱 실패: sessionId={}, err={}", wsSessionId, e.toString());
      return;
    }

    if (!"audience".equalsIgnoreCase(role) || roomId == null || audienceId == null) {
      return; // 발표자/식별 불가 세션은 청중 수에 영향 없음
    }

    log.debug("[WebSocket] CONNECT 청중 등록: sessionId={}, roomId={}, audienceId={}",
        wsSessionId, roomId, audienceId);
    try {
      roomService.registerAudiencePresence(roomId, audienceId, wsSessionId);
    } catch (Exception e) {
      log.error("[WebSocket] 청중 등록 실패: sessionId={}, err={}", wsSessionId, e.toString());
    }
  }

  @EventListener
  public void handleSessionDisconnect(SessionDisconnectEvent event) {
    String wsSessionId = event.getSessionId();
    log.debug("[WebSocket] 연결 종료: sessionId={}", wsSessionId);

    if (wsSessionId == null) {
      return;
    }

    try {
      roomService.unregisterAudienceBySession(wsSessionId);
    } catch (Exception e) {
      log.error("[WebSocket] 청중 퇴장 처리 실패: sessionId={}, err={}", wsSessionId, e.toString());
    }
  }

  private static String firstNative(StompHeaderAccessor acc, String name) {
    var values = acc.getNativeHeader(name);
    return (values == null || values.isEmpty()) ? null : values.get(0);
  }
}
