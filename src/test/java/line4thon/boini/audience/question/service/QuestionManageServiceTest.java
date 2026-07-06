package line4thon.boini.audience.question.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import line4thon.boini.audience.question.dto.ClusterItem;
import line4thon.boini.audience.question.dto.QuestionStatusEvent;
import line4thon.boini.audience.question.dto.response.ClusterReportResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class QuestionManageServiceTest {

  private StringRedisTemplate redis;
  private HashOperations<String, Object, Object> hashOps;
  private SetOperations<String, String> setOps;
  private SimpMessagingTemplate broker;
  private ClusterBroadcaster clusterBroadcaster;
  private QuestionManageService service;

  @BeforeEach
  void setUp() {
    redis = mock(StringRedisTemplate.class);
    hashOps = mock(HashOperations.class);
    setOps = mock(SetOperations.class);
    broker = mock(SimpMessagingTemplate.class);
    clusterBroadcaster = mock(ClusterBroadcaster.class);

    when(redis.opsForHash()).thenReturn(hashOps);
    when(redis.opsForSet()).thenReturn(setOps);

    service = new QuestionManageService(redis, broker, clusterBroadcaster);
  }

  @Test
  void completeRepresentativeCompletesEveryActiveQuestionInCluster() {
    when(clusterBroadcaster.getCurrentClusters("room-1")).thenReturn(clusterReport());

    service.complete("room-1", "q1");

    verify(hashOps).put("room:room-1:question:q1", "status", "completed");
    verify(hashOps).put("room:room-1:question:q2", "status", "completed");
    verify(setOps).add("room:room-1:questions:completed", "q1");
    verify(setOps).add("room:room-1:questions:completed", "q2");
    verify(broker, times(2))
        .convertAndSend(eq("/topic/p/room-1/public"), any(QuestionStatusEvent.class));
    verify(broker, times(2))
        .convertAndSend(eq("/topic/p/room-1/presenter"), any(QuestionStatusEvent.class));
    verify(clusterBroadcaster).refreshAndBroadcast("room-1");
  }

  @Test
  void completeSubQuestionCompletesOnlyClickedQuestion() {
    when(clusterBroadcaster.getCurrentClusters("room-1")).thenReturn(clusterReport());

    service.complete("room-1", "q2");

    verify(hashOps, never()).put("room:room-1:question:q1", "status", "completed");
    verify(hashOps).put("room:room-1:question:q2", "status", "completed");
    verify(setOps, never()).add("room:room-1:questions:completed", "q1");
    verify(setOps).add("room:room-1:questions:completed", "q2");
    verify(clusterBroadcaster).refreshAndBroadcast("room-1");
  }

  @Test
  void deleteRemovesQuestionFromCompletedSet() {
    when(clusterBroadcaster.getCurrentClusters("room-1")).thenReturn(null);

    service.delete("room-1", "q1");

    verify(hashOps).put("room:room-1:question:q1", "status", "deleted");
    verify(setOps).add("room:room-1:questions:deleted", "q1");
    verify(setOps).remove("room:room-1:questions:completed", "q1");
  }

  @Test
  void listCompletedSkipsDeletedOrStaleCompletedSetEntries() {
    when(setOps.members("room:room-1:questions:completed")).thenReturn(Set.of("q1", "q2"));
    when(hashOps.entries("room:room-1:question:q1")).thenReturn(Map.of(
        "id", "q1",
        "roomId", "room-1",
        "slide", "1",
        "audienceId", "a1",
        "content", "completed",
        "ts", "100",
        "status", "completed"
    ));
    when(hashOps.entries("room:room-1:question:q2")).thenReturn(Map.of(
        "id", "q2",
        "roomId", "room-1",
        "slide", "1",
        "audienceId", "a2",
        "content", "deleted",
        "ts", "200",
        "status", "deleted"
    ));

    var completed = service.listCompleted("room-1");

    assertThat(completed).hasSize(1);
    assertThat(completed.get(0).id()).isEqualTo("q1");
  }

  private ClusterReportResponse clusterReport() {
    ClusterItem cluster = new ClusterItem(
        "cluster-q1",
        "q1",
        "대표 질문",
        2,
        List.of(),
        List.of("q1", "q2"),
        List.of(1, 2),
        List.of("대표 질문", "하위 질문")
    );

    return new ClusterReportResponse("room-1", 2, 1, List.of(cluster));
  }
}
