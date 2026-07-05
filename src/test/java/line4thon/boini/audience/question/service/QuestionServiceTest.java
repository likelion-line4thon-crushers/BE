package line4thon.boini.audience.question.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import line4thon.boini.audience.question.dto.requeset.CreateQuestionRequest;
import line4thon.boini.audience.question.exception.AudienceQuestionErrorCode;
import line4thon.boini.global.common.exception.CustomException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class QuestionServiceTest {

  @Test
  void createRejectsWhenQuestionOptionDisabled() {
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    SimpMessagingTemplate broker = mock(SimpMessagingTemplate.class);
    RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
    ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    ClusterBroadcaster clusterBroadcaster = mock(ClusterBroadcaster.class);
    QuestionService service = new QuestionService(redis, broker, redisTemplate, clusterBroadcaster);
    CreateQuestionRequest request = new CreateQuestionRequest("aud-1", 1, "질문입니다.", 123L);

    when(redisTemplate.opsForValue()).thenReturn(valueOps);
    when(valueOps.get("room:room-1:option:question")).thenReturn("false");

    assertThatThrownBy(() -> service.create("room-1", request))
        .isInstanceOfSatisfying(CustomException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(AudienceQuestionErrorCode.QUESTION_DISABLED)
        );

    verify(valueOps, never()).increment("room:room-1:questionCount");
    verify(redis, never()).opsForValue();
    verify(redis, never()).opsForHash();
    verify(redis, never()).opsForZSet();
    verify(broker, never()).convertAndSend(anyString(), any(Object.class));
    verify(clusterBroadcaster, never()).broadcast(anyString(), any());
  }
}
