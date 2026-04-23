package line4thon.boini.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

  /**
   * FastAPI 서버로 HTTP 요청을 보내는 WebClient 빈.
   * baseUrl은 AppProperties.fastApi.baseUrl 에서 주입
   */
  @Bean
  public WebClient fastApiWebClient(AppProperties props) {
    return WebClient.builder()
        .baseUrl(props.getFastApi().getBaseUrl())
        .build();
  }
}
