package line4thon.boini.audience.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.IntStream;
import line4thon.boini.audience.feedback.dto.request.SaveFeedbackQuestionsRequest;
import line4thon.boini.audience.feedback.dto.request.SaveFeedbackQuestionsRequest.QuestionItem;
import line4thon.boini.audience.feedback.dto.response.FeedbackQuestionsResponse;
import line4thon.boini.audience.feedback.repository.FeedbackQuestionRepository;
import line4thon.boini.audience.feedback.service.FeedbackQuestionService;
import line4thon.boini.global.common.exception.CustomException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class FeedbackQuestionServiceTest {

  @Autowired FeedbackQuestionService service;
  @Autowired FeedbackQuestionRepository repository;

  private SaveFeedbackQuestionsRequest req(String... texts) {
    SaveFeedbackQuestionsRequest r = new SaveFeedbackQuestionsRequest();
    List<QuestionItem> items = IntStream.range(0, texts.length)
        .mapToObj(i -> new QuestionItem(i, texts[i]))
        .toList();
    r.setQuestions(items);
    return r;
  }

  @Test
  void replaceInsertsThenReplacesWholeSet() {
    service.replace("room1", req("q1", "q2"));
    FeedbackQuestionsResponse after = service.replace("room1", req("only"));

    assertThat(after.getQuestions()).hasSize(1);
    assertThat(after.getQuestions().get(0).getQuestionText()).isEqualTo("only");
    assertThat(repository.findByRoomIdOrderByOrderIndexAsc("room1")).hasSize(1);
  }

  @Test
  void emptySetClearsForm() {
    service.replace("room1", req("q1"));
    FeedbackQuestionsResponse after = service.replace("room1", req());
    assertThat(after.getQuestions()).isEmpty();
  }

  @Test
  void moreThanTwentyRejected() {
    String[] texts = IntStream.range(0, 21).mapToObj(i -> "q" + i).toArray(String[]::new);
    assertThatThrownBy(() -> service.replace("room1", req(texts)))
        .isInstanceOf(CustomException.class);
  }

  @Test
  void listReturnsQuestionsOrderedByOrderIndex() {
    service.replace("room1", req("q1", "q2", "q3"));

    FeedbackQuestionsResponse response = service.list("room1");

    assertThat(response.getQuestions())
        .extracting(FeedbackQuestionsResponse.Item::getQuestionText)
        .containsExactly("q1", "q2", "q3");
    assertThat(response.getQuestions())
        .extracting(FeedbackQuestionsResponse.Item::getOrderIndex)
        .containsExactly(0, 1, 2);
  }

  @Test
  void blankRoomIdThrowsEmptyRoomId() {
    assertThatThrownBy(() -> service.list("  ")).isInstanceOf(CustomException.class);
    assertThatThrownBy(() -> service.replace("  ", req("q"))).isInstanceOf(CustomException.class);
  }

  @Test
  void overLongQuestionRejected() {
    String longText = "a".repeat(501);
    assertThatThrownBy(() -> service.replace("room1", req(longText)))
        .isInstanceOf(CustomException.class);
  }

  @Test
  void blankQuestionsAreDroppedAndReindexed() {
    FeedbackQuestionsResponse response = service.replace("room1", req("q1", "   ", "q2"));

    assertThat(response.getQuestions())
        .extracting(FeedbackQuestionsResponse.Item::getQuestionText)
        .containsExactly("q1", "q2");
    assertThat(response.getQuestions())
        .extracting(FeedbackQuestionsResponse.Item::getOrderIndex)
        .containsExactly(0, 1);
  }
}
