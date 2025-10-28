package line4thon.boini.global.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
@Slf4j
public class WebSocketEventListener {

  @EventListener
  public void handleSessionConnected(SessionConnectEvent event) {
    log.info("[WebSocket] 클라이언트 연결됨: {}", event.getMessage().getHeaders());
  }

  @EventListener
  public void handleSessionDisconnect(SessionDisconnectEvent event) {
    log.info("[WebSocket] 클라이언트 연결 종료: sessionId={}", event.getSessionId());
  }
}
