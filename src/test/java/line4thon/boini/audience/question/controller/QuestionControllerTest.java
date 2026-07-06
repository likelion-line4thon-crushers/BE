package line4thon.boini.audience.question.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import line4thon.boini.audience.question.dto.requeset.QuestionLikeRequest;
import line4thon.boini.audience.question.service.QuestionService;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.MessagingException;

class QuestionControllerTest {

  @Test
  void likeViaWsAllowsLegacyAnonymousPrincipal() {
    QuestionService questionService = mock(QuestionService.class);
    QuestionController controller = new QuestionController(questionService);
    QuestionLikeRequest request = new QuestionLikeRequest("q1", "aud-1", true);

    controller.likeViaWs("room-1", request, () -> "anon");

    verify(questionService).updateLike("room-1", request);
  }

  @Test
  void likeViaWsAllowsMatchingVerifiedPrincipal() {
    QuestionService questionService = mock(QuestionService.class);
    QuestionController controller = new QuestionController(questionService);
    QuestionLikeRequest request = new QuestionLikeRequest("q1", "aud-1", true);

    controller.likeViaWs("room-1", request, () -> "aud-1");

    verify(questionService).updateLike("room-1", request);
  }

  @Test
  void likeViaWsRejectsMismatchedVerifiedPrincipal() {
    QuestionService questionService = mock(QuestionService.class);
    QuestionController controller = new QuestionController(questionService);
    QuestionLikeRequest request = new QuestionLikeRequest("q1", "aud-1", true);

    assertThatThrownBy(() -> controller.likeViaWs("room-1", request, () -> "aud-2"))
        .isInstanceOf(MessagingException.class);

    verify(questionService, never()).updateLike("room-1", request);
  }
}
