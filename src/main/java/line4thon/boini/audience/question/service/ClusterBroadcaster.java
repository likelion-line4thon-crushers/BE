package line4thon.boini.audience.question.service;

import line4thon.boini.audience.question.dto.ClusterEvent;
import line4thon.boini.audience.question.dto.requeset.QuestionInput;
import line4thon.boini.audience.question.dto.response.FastApiClusterResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClusterBroadcaster {

  private final WebClient fastApiWebClient;
  private final SimpMessagingTemplate broker;

  /**
   * 새 질문 데이터를 FastAPI 증분 클러스터링 엔드포인트로 전송하고,
   * 응답받은 클러스터 결과를 /topic/p/{roomId}/clusters 로 WebSocket broadcast한다.
   * @Async("clusterExecutor") 로 별도 스레드풀에서 실행되어 질문 저장 흐름을 블로킹하지 않는다.
   * 같은 클래스 내부 호출 시 @Async가 동작하지 않는 Spring 프록시 제약 때문에 별도 컴포넌트로 분리했다.
   */
  @Async("clusterExecutor")
  public void broadcast(String roomId, QuestionInput input) {
    try {
      FastApiClusterResponse resp = fastApiWebClient.post()
          .uri("/report/questions/rooms/{roomId}/clusters/incremental", roomId)
          .bodyValue(input)
          .retrieve()
          .bodyToMono(FastApiClusterResponse.class)
          .block(Duration.ofSeconds(10));

      if (resp != null && resp.data() != null) {
        broker.convertAndSend(
            "/topic/p/" + roomId + "/clusters",
            ClusterEvent.updated(resp.data())
        );
        log.info("[CLUSTER] broadcast 완료 (roomId={}, groups={})",
            roomId, resp.data().uniqueGroups());
      }
    } catch (Exception e) {
      log.warn("[CLUSTER] FastAPI 호출 실패 (roomId={}): {}", roomId, e.getMessage());
    }
  }
}
