package line4thon.boini.audience.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import line4thon.boini.audience.feedback.dto.response.FeedbackReportResponse;
import line4thon.boini.audience.feedback.entity.FeedbackEntity;
import line4thon.boini.audience.feedback.repository.FeedbackRepository;
import line4thon.boini.audience.feedback.service.ChatGptService;
import line4thon.boini.audience.feedback.service.FeedbackReportService;
import line4thon.boini.global.common.exception.CustomException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FeedbackReportServiceTest {

  @Mock FeedbackRepository feedbackRepository;
  @Mock ChatGptService chatGptService;
  @InjectMocks FeedbackReportService service;

  private FeedbackEntity f(String aud, int rating, String comment) {
    return FeedbackEntity.builder()
        .roomId("r1").audienceId(aud).rating(rating).comment(comment).createdAt(Instant.now()).build();
  }

  // n feedbacks with non-blank comments.
  private List<FeedbackEntity> feedbacks(int n) {
    List<FeedbackEntity> list = new ArrayList<>();
    for (int i = 0; i < n; i++) list.add(f("u" + i, 5, "comment" + i));
    return list;
  }

  private static final String LOCKED_MESSAGE = "후기가 10개를 넘게 모이면 AI 요약이 제공됩니다.";

  @Test
  void summarizationDisabledWhenCommentsAtOrBelowThreshold() {
    when(feedbackRepository.findByRoomIdOrderByCreatedAtDesc("r1")).thenReturn(feedbacks(10));

    FeedbackReportResponse resp = service.getFeedbacksByRoom("r1");

    assertThat(resp.isSummarizationEnabled()).isFalse();
    assertThat(resp.getSummary()).isEqualTo(LOCKED_MESSAGE);
    assertThat(resp.getCount()).isEqualTo(10);
    verify(chatGptService, never()).summarizeFeedbackComments(anyList(), anyDouble(), anyInt());
  }

  @Test
  void summarizesWhenCommentsExceedThreshold() {
    when(feedbackRepository.findByRoomIdOrderByCreatedAtDesc("r1")).thenReturn(feedbacks(11));
    when(chatGptService.summarizeFeedbackComments(anyList(), anyDouble(), anyInt())).thenReturn("sum");

    FeedbackReportResponse resp = service.getFeedbacksByRoom("r1");

    assertThat(resp.isSummarizationEnabled()).isTrue();
    assertThat(resp.getSummary()).isEqualTo("sum");
    assertThat(resp.getCount()).isEqualTo(11);
  }

  @Test
  void blankCommentsDoNotCountTowardThreshold() {
    // 후기 12개지만 코멘트는 10개(공백/누락 2개) -> 요약 비활성화.
    List<FeedbackEntity> list = feedbacks(10);
    list.add(f("u10", 4, "  "));
    list.add(f("u11", 3, null));
    when(feedbackRepository.findByRoomIdOrderByCreatedAtDesc("r1")).thenReturn(list);

    FeedbackReportResponse resp = service.getFeedbacksByRoom("r1");

    assertThat(resp.isSummarizationEnabled()).isFalse();
    assertThat(resp.getSummary()).isEqualTo(LOCKED_MESSAGE);
    assertThat(resp.getCount()).isEqualTo(12);
    verify(chatGptService, never()).summarizeFeedbackComments(anyList(), anyDouble(), anyInt());
  }

  @Test
  void reusesCachedSummaryUntilCommentCountChanges() {
    when(feedbackRepository.findByRoomIdOrderByCreatedAtDesc("r1")).thenReturn(feedbacks(11));
    when(chatGptService.summarizeFeedbackComments(anyList(), anyDouble(), anyInt())).thenReturn("sum");

    service.getFeedbacksByRoom("r1");
    service.getFeedbacksByRoom("r1"); // 코멘트 수 동일 -> 캐시 재사용
    verify(chatGptService, times(1)).summarizeFeedbackComments(anyList(), anyDouble(), anyInt());

    // 새 후기가 추가되면(12개) 재요약
    when(feedbackRepository.findByRoomIdOrderByCreatedAtDesc("r1")).thenReturn(feedbacks(12));
    service.getFeedbacksByRoom("r1");
    verify(chatGptService, times(2)).summarizeFeedbackComments(anyList(), anyDouble(), anyInt());
  }

  @Test
  void emptyRoomReturnsDefaultsWithoutSummarization() {
    when(feedbackRepository.findByRoomIdOrderByCreatedAtDesc("r1")).thenReturn(List.of());

    FeedbackReportResponse resp = service.getFeedbacksByRoom("r1");

    assertThat(resp.isSummarizationEnabled()).isFalse();
    assertThat(resp.getSummary()).isEqualTo("청중 후기가 없습니다.");
    assertThat(resp.getCount()).isZero();
    assertThat(resp.getFeedbacks()).isEmpty();
    verify(chatGptService, never()).summarizeFeedbackComments(anyList(), anyDouble(), anyInt());
  }

  @Test
  void blankRoomIdRejected() {
    assertThatThrownBy(() -> service.getFeedbacksByRoom("  ")).isInstanceOf(CustomException.class);
  }
}
