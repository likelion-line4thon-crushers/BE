package line4thon.boini.audience.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import line4thon.boini.audience.feedback.dto.request.SubmitFeedbackAnswersRequest;
import line4thon.boini.audience.feedback.dto.request.SubmitFeedbackAnswersRequest.AnswerItem;
import line4thon.boini.audience.feedback.dto.response.FeedbackAnswersResponse;
import line4thon.boini.audience.feedback.entity.FeedbackAnswerEntity;
import line4thon.boini.audience.feedback.repository.FeedbackAnswerRepository;
import line4thon.boini.audience.feedback.service.FeedbackAnswerService;
import line4thon.boini.global.common.exception.CustomException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class FeedbackAnswerServiceTest {

  @Autowired FeedbackAnswerService service;
  @Autowired FeedbackAnswerRepository repository;

  private SubmitFeedbackAnswersRequest req(String audienceId, AnswerItem... items) {
    SubmitFeedbackAnswersRequest r = new SubmitFeedbackAnswersRequest();
    r.setAudienceId(audienceId);
    r.setAnswers(List.of(items));
    return r;
  }

  @Test
  void savesAnswers() {
    FeedbackAnswersResponse resp =
        service.submit("room1", req("aud1", new AnswerItem(10L, "좋았어요"), new AnswerItem(11L, "별로")));
    assertThat(resp.getAnswers()).hasSize(2);
    assertThat(repository.findByRoomIdAndAudienceId("room1", "aud1")).hasSize(2);
  }

  @Test
  void resubmitReplacesPreviousAnswers() {
    service.submit("room1", req("aud1", new AnswerItem(10L, "first")));
    service.submit("room1", req("aud1", new AnswerItem(10L, "second"), new AnswerItem(11L, "more")));
    assertThat(repository.findByRoomIdAndAudienceId("room1", "aud1")).hasSize(2);
    assertThat(repository.findByRoomIdAndAudienceId("room1", "aud1"))
        .extracting(FeedbackAnswerEntity::getAnswerText)
        .contains("second", "more")
        .doesNotContain("first");
  }

  @Test
  void blankRoomIdRejected() {
    assertThatThrownBy(() -> service.submit("  ", req("aud1", new AnswerItem(10L, "x"))))
        .isInstanceOf(CustomException.class);
  }

  @Test
  void blankAudienceIdRejected() {
    assertThatThrownBy(() -> service.submit("room1", req("  ", new AnswerItem(10L, "x"))))
        .isInstanceOf(CustomException.class);
  }

  @Test
  void emptyAnswersAccepted() {
    assertThatCode(() -> service.submit("room1", req("aud1"))).doesNotThrowAnyException();

    FeedbackAnswersResponse resp = service.submit("room1", req("aud1"));
    assertThat(resp.getAnswers()).isEmpty();
    assertThat(repository.findByRoomIdAndAudienceId("room1", "aud1")).isEmpty();
  }
}
