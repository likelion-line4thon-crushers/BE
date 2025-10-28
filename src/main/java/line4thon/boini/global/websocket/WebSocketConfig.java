package line4thon.boini.global.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  private final StompAuthChannelInterceptor authInterceptor;

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {

    // 청중 전용
    registry.addEndpoint("/ws/audience")
        .setAllowedOriginPatterns(
            "http://localhost:*",
            "https://line4thon-boini.netlify.app"
        )
        .withSockJS();

    // 발표자 전용
    registry.addEndpoint("/ws/presenter")
        .setAllowedOriginPatterns(
            "http://localhost:*",
            "https://line4thon-boini.netlify.app"
        )
        .withSockJS();
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry.enableSimpleBroker("/topic", "/queue");  // 서버 → 클라 (broadcast)
    registry.setApplicationDestinationPrefixes("/app"); // 클라 → 서버
  }
}
