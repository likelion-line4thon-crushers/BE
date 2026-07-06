package line4thon.boini.audience.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import line4thon.boini.audience.feedback.dto.response.AudienceVoiceResponse;
import line4thon.boini.audience.feedback.entity.FeedbackAnswerEntity;
import line4thon.boini.audience.feedback.entity.FeedbackEntity;
import line4thon.boini.audience.feedback.entity.FeedbackQuestionEntity;
import line4thon.boini.audience.feedback.repository.FeedbackAnswerRepository;
import line4thon.boini.audience.feedback.repository.FeedbackQuestionRepository;
import line4thon.boini.audience.feedback.repository.FeedbackRepository;
import line4thon.boini.audience.feedback.service.AudienceVoiceReportService;
import line4thon.boini.audience.feedback.service.ChatGptService;
import line4thon.boini.global.common.exception.CustomException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AudienceVoiceReportServiceTest {

  @Mock FeedbackQuestionRepository questionRepository;
  @Mock FeedbackAnswerRepository answerRepository;
  @Mock FeedbackRepository feedbackRepository;
  @Mock ChatGptService chatGptService;
  @InjectMocks AudienceVoiceReportService service;

  private FeedbackQuestionEntity q(long id, int order, String text) {
    return FeedbackQuestionEntity.builder().id(id).roomId("r1").orderIndex(order).questionText(text).build();
  }

  private FeedbackAnswerEntity a(long qid, String aud, String text) {
    return FeedbackAnswerEntity.builder()
        .roomId("r1").audienceId(aud).questionId(qid).answerText(text).createdAt(Instant.now()).build();
  }

  private FeedbackEntity f(int rating) {
    return FeedbackEntity.builder().roomId("r1").audienceId("x").rating(rating).comment("").createdAt(Instant.now()).build();
  }

  // n non-blank answers for one question.
  private List<FeedbackAnswerEntity> answers(long qid, int n) {
    List<FeedbackAnswerEntity> list = new ArrayList<>();
    for (int i = 0; i < n; i++) list.add(a(qid, "u" + i, "ans" + i));
    return list;
  }

  @Test
  void summarizationDisabledWhenAnyQuestionAtOrBelowThreshold() {
    when(questionRepository.findByRoomIdOrderByOrderIndexAsc("r1"))
        .thenReturn(List.of(q(10, 0, "Q1"), q(11, 1, "Q2")));
    when(answerRepository.findByRoomId("r1"))
        .thenReturn(List.of(a(10, "u1", "a-one"), a(10, "u2", "a-two"), a(11, "u1", "b-one")));
    when(feedbackRepository.findByRoomIdOrderByCreatedAtDesc("r1")).thenReturn(List.of(f(4), f(5)));

    AudienceVoiceResponse resp = service.getReport("r1");

    assertThat(resp.isHasQuestions()).isTrue();
    assertThat(resp.isSummarizationEnabled()).isFalse();
    assertThat(resp.getAverageRating()).isEqualTo(4.5);
    assertThat(resp.getQuestions()).hasSize(2);
    assertThat(resp.getQuestions().get(0).getAnswers()).containsExactly("a-one", "a-two");
    assertThat(resp.getQuestions().get(0).getAnswerCount()).isEqualTo(2);
    assertThat(resp.getQuestions().get(0).getSummary()).isNull();
    verify(chatGptService, never()).summarizeQuestionAnswers(anyString(), anyList());
  }

  @Test
  void summarizesWhenEveryQuestionExceedsThreshold() {
    when(questionRepository.findByRoomIdOrderByOrderIndexAsc("r1"))
        .thenReturn(List.of(q(10, 0, "Q1"), q(11, 1, "Q2")));
    List<FeedbackAnswerEntity> all = new ArrayList<>();
    all.addAll(answers(10, 6));
    all.addAll(answers(11, 6));
    when(answerRepository.findByRoomId("r1")).thenReturn(all);
    when(feedbackRepository.findByRoomIdOrderByCreatedAtDesc("r1")).thenReturn(List.of(f(5)));
    when(chatGptService.summarizeQuestionAnswers(anyString(), anyList())).thenReturn("sum");

    AudienceVoiceResponse resp = service.getReport("r1");

    assertThat(resp.isSummarizationEnabled()).isTrue();
    assertThat(resp.getQuestions().get(0).getAnswerCount()).isEqualTo(6);
    assertThat(resp.getQuestions().get(0).getSummary()).isEqualTo("sum");
    verify(chatGptService, times(2)).summarizeQuestionAnswers(anyString(), anyList());
  }

  @Test
  void reusesCachedSummaryUntilAnswerCountIncreases() {
    when(questionRepository.findByRoomIdOrderByOrderIndexAsc("r1"))
        .thenReturn(List.of(q(10, 0, "Q1"), q(11, 1, "Q2")));
    when(feedbackRepository.findByRoomIdOrderByCreatedAtDesc("r1")).thenReturn(List.of(f(5)));
    when(chatGptService.summarizeQuestionAnswers(anyString(), anyList())).thenReturn("sum");

    List<FeedbackAnswerEntity> six = new ArrayList<>();
    six.addAll(answers(10, 6));
    six.addAll(answers(11, 6));
    when(answerRepository.findByRoomId("r1")).thenReturn(six);

    service.getReport("r1");
    service.getReport("r1"); // same counts -> summaries reused from cache
    verify(chatGptService, times(2)).summarizeQuestionAnswers(anyString(), anyList());

    // Q1 gets a new answer (7), Q2 unchanged (6) -> only Q1 re-summarized
    List<FeedbackAnswerEntity> updated = new ArrayList<>();
    updated.addAll(answers(10, 7));
    updated.addAll(answers(11, 6));
    when(answerRepository.findByRoomId("r1")).thenReturn(updated);

    service.getReport("r1");
    verify(chatGptService, times(3)).summarizeQuestionAnswers(anyString(), anyList());
  }

  @Test
  void questionWithNoAnswersGetsEmptyListAndNoSummary() {
    when(questionRepository.findByRoomIdOrderByOrderIndexAsc("r1"))
        .thenReturn(List.of(q(10, 0, "Q1"), q(11, 1, "Q2"), q(12, 2, "Q3")));
    when(answerRepository.findByRoomId("r1"))
        .thenReturn(List.of(a(10, "u1", "a-one"), a(10, "u2", "a-two"), a(11, "u1", "b-one")));
    when(feedbackRepository.findByRoomIdOrderByCreatedAtDesc("r1")).thenReturn(List.of(f(4), f(5)));

    AudienceVoiceResponse resp = service.getReport("r1");

    assertThat(resp.isSummarizationEnabled()).isFalse();
    assertThat(resp.getQuestions()).hasSize(3);
    assertThat(resp.getQuestions().get(2).getQuestionText()).isEqualTo("Q3");
    assertThat(resp.getQuestions().get(2).getAnswers()).isEmpty();
    assertThat(resp.getQuestions().get(2).getAnswerCount()).isZero();
    verify(chatGptService, never()).summarizeQuestionAnswers(anyString(), anyList());
  }

  @Test
  void noQuestionsReturnsHasQuestionsFalseWithAverage() {
    when(questionRepository.findByRoomIdOrderByOrderIndexAsc("r1")).thenReturn(List.of());
    when(feedbackRepository.findByRoomIdOrderByCreatedAtDesc("r1")).thenReturn(List.of(f(3), f(5)));

    AudienceVoiceResponse resp = service.getReport("r1");

    assertThat(resp.isHasQuestions()).isFalse();
    assertThat(resp.isSummarizationEnabled()).isFalse();
    assertThat(resp.getQuestions()).isEmpty();
    assertThat(resp.getAverageRating()).isEqualTo(4.0);
  }

  @Test
  void blankRoomIdRejected() {
    assertThatThrownBy(() -> service.getReport("  ")).isInstanceOf(CustomException.class);
  }
}
