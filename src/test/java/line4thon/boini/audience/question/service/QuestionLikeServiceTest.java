package line4thon.boini.audience.question.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import line4thon.boini.audience.question.dto.QuestionLikeEvent;
import line4thon.boini.audience.question.dto.requeset.QuestionLikeRequest;
import line4thon.boini.audience.question.exception.AudienceQuestionErrorCode;
import line4thon.boini.global.common.exception.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class QuestionLikeServiceTest {

  private StringRedisTemplate redis;
  private HashOperations<String, Object, Object> hashOps;
  private SetOperations<String, String> setOps;
  private ZSetOperations<String, String> zSetOps;
  private SimpMessagingTemplate broker;
  private QuestionService service;

  @BeforeEach
  void setUp() {
    redis = mock(StringRedisTemplate.class);
    hashOps = mock(HashOperations.class);
    setOps = mock(SetOperations.class);
    zSetOps = mock(ZSetOperations.class);
    broker = mock(SimpMessagingTemplate.class);

    RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
    ClusterBroadcaster clusterBroadcaster = mock(ClusterBroadcaster.class);

    when(redis.opsForHash()).thenReturn(hashOps);
    when(redis.opsForSet()).thenReturn(setOps);
    when(redis.opsForZSet()).thenReturn(zSetOps);

    service = new QuestionService(redis, broker, redisTemplate, clusterBroadcaster);
  }

  @Test
  void updateLikeStoresAudienceMembershipAndBroadcastsCount() {
    when(redis.execute(
        any(DefaultRedisScript.class),
        eq(List.of("room:room-1:question:q1", "room:room-1:question:q1:likes")),
        eq("aud-1"),
        eq("true")
    )).thenReturn(3L);

    QuestionLikeEvent event = service.updateLike(
        "room-1",
        new QuestionLikeRequest("q1", "aud-1", true)
    );

    verify(redis).execute(
        any(DefaultRedisScript.class),
        eq(List.of("room:room-1:question:q1", "room:room-1:question:q1:likes")),
        eq("aud-1"),
        eq("true")
    );
    verify(broker).convertAndSend(eq("/topic/p/room-1/public"), any(QuestionLikeEvent.class));
    verify(broker).convertAndSend(eq("/topic/p/room-1/presenter"), any(QuestionLikeEvent.class));
    assertThat(event.questionId()).isEqualTo("q1");
    assertThat(event.audienceId()).isEqualTo("aud-1");
    assertThat(event.liked()).isTrue();
    assertThat(event.likeCount()).isEqualTo(3);
  }

  @Test
  void updateLikeRemovesAudienceMembershipWhenUnliked() {
    when(redis.execute(
        any(DefaultRedisScript.class),
        eq(List.of("room:room-1:question:q1", "room:room-1:question:q1:likes")),
        eq("aud-1"),
        eq("false")
    )).thenReturn(1L);

    QuestionLikeEvent event = service.updateLike(
        "room-1",
        new QuestionLikeRequest("q1", "aud-1", false)
    );

    assertThat(event.liked()).isFalse();
    assertThat(event.likeCount()).isEqualTo(1);
  }

  @Test
  void updateLikeRejectsUnknownQuestion() {
    when(redis.execute(
        any(DefaultRedisScript.class),
        eq(List.of("room:room-1:question:q-missing", "room:room-1:question:q-missing:likes")),
        eq("aud-1"),
        eq("true")
    )).thenReturn(-1L);

    assertThatThrownBy(() -> service.updateLike(
        "room-1",
        new QuestionLikeRequest("q-missing", "aud-1", true)
    ))
        .isInstanceOfSatisfying(CustomException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(AudienceQuestionErrorCode.INVALID_QUESTION_ID)
        );

    verify(broker, never()).convertAndSend(any(String.class), any(Object.class));
  }

  @Test
  void updateLikeRejectsCompletedQuestion() {
    when(redis.execute(
        any(DefaultRedisScript.class),
        eq(List.of("room:room-1:question:q1", "room:room-1:question:q1:likes")),
        eq("aud-1"),
        eq("true")
    )).thenReturn(-2L);

    assertThatThrownBy(() -> service.updateLike(
        "room-1",
        new QuestionLikeRequest("q1", "aud-1", true)
    ))
        .isInstanceOfSatisfying(CustomException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(AudienceQuestionErrorCode.QUESTION_NOT_ACTIVE)
        );

    verify(broker, never()).convertAndSend(any(String.class), any(Object.class));
  }

  @Test
  void listRoomReturnsLikedByMeForAudience() {
    ZSetOperations.TypedTuple<String> tuple = ZSetOperations.TypedTuple.of("q1", 100.0);
    when(zSetOps.rangeByScoreWithScores("room:room-1:questions", Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY))
        .thenReturn(Set.of(tuple));
    when(hashOps.entries("room:room-1:question:q1")).thenReturn(Map.of(
        "id", "q1",
        "roomId", "room-1",
        "slide", "1",
        "audienceId", "author-1",
        "content", "liked question",
        "ts", "100",
        "status", "active",
        "likeCount", "4"
    ));
    when(setOps.isMember("room:room-1:question:q1:likes", "aud-1")).thenReturn(true);

    var questions = service.listRoom("room-1", null, null, "aud-1");

    assertThat(questions).hasSize(1);
    assertThat(questions.get(0).likeCount()).isEqualTo(4);
    assertThat(questions.get(0).likedByMe()).isTrue();
  }
}
