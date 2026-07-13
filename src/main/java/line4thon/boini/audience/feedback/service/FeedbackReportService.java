package line4thon.boini.audience.feedback.service;

import line4thon.boini.audience.feedback.dto.FeedbackItemDto;
import line4thon.boini.audience.feedback.exception.FeedbackErrorCode;
import line4thon.boini.global.common.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import line4thon.boini.audience.feedback.dto.response.*;
import line4thon.boini.audience.feedback.entity.FeedbackEntity;
import line4thon.boini.audience.feedback.repository.FeedbackRepository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class FeedbackReportService {

  // AI 요약 활성화 임계값: 비어 있지 않은 후기 코멘트가 이 개수를 "초과"해야 함.
  private static final int SUMMARY_THRESHOLD = 10;

  // 요약 비활성화 시 대신 내려주는 안내 문구 (청중의 목소리 프론트 문구와 톤 통일).
  private static final String SUMMARY_LOCKED_MESSAGE =
      "후기가 " + SUMMARY_THRESHOLD + "개를 넘게 모이면 AI 요약이 제공됩니다.";

  private final FeedbackRepository feedbackRepository;
  private final ChatGptService chatGptService;

  // 방별 요약 캐시: key = roomId. 코멘트 수가 변했을 때만 재생성한다.
  private final Map<String, CachedSummary> summaryCache = new ConcurrentHashMap<>();

  private record CachedSummary(int commentCount, String summary) {}


  public FeedbackReportResponse getFeedbacksByRoom(String roomId) {

    if (roomId == null || roomId.isBlank()) {
      throw new CustomException(FeedbackErrorCode.EMPTY_ROOM_ID);
    }

    try {
      List<FeedbackEntity> list = feedbackRepository.findByRoomIdOrderByCreatedAtDesc(roomId);

      if (list.isEmpty()) {
        return FeedbackReportResponse.builder()
            .averageRating(0)
            .count(0)
            .summarizationEnabled(false)
            .summary("청중 후기가 없습니다.")
            .feedbacks(List.of())
            .build();
      }

      double avg = list.stream()
          .mapToInt(FeedbackEntity::getRating)
          .average()
          .orElse(0.0);

      List<String> comments = list.stream()
          .map(FeedbackEntity::getComment)
          .filter(c -> c != null && !c.isBlank())
          .toList();

      // 코멘트가 10개를 "초과"할 때만 AI 요약 활성화. 그 전에는 안내 문구를 내려준다.
      boolean summarizationEnabled = comments.size() > SUMMARY_THRESHOLD;

      String summary = summarizationEnabled
          ? summaryFor(roomId, comments, avg)
          : SUMMARY_LOCKED_MESSAGE;

      List<FeedbackItemDto> items = list.stream()
          .map(f -> FeedbackItemDto.builder()
              .audienceId(f.getAudienceId())
              .rating(f.getRating())
              .comment(f.getComment())
              .createdAt(f.getCreatedAt())
              .build())
          .toList();

      return FeedbackReportResponse.builder()
          .averageRating(Math.round(avg * 10.0) / 10.0) // 소수 1자리
          .count(items.size())
          .summarizationEnabled(summarizationEnabled)
          .summary(summary)
          .feedbacks(items)
          .build();

    } catch (CustomException e) {
      throw e;
    } catch (Exception e) {
      throw new CustomException(FeedbackErrorCode.FETCH_FAILED);
    }
  }

  // 코멘트 수가 캐시된 값과 같으면(=새 후기 없음) 캐시를 재사용하고, 변했을 때만 재요약한다.
  private String summaryFor(String roomId, List<String> comments, double average) {
    int count = comments.size();
    CachedSummary cached = summaryCache.get(roomId);
    if (cached != null && cached.commentCount() == count) {
      return cached.summary();
    }
    String summary = chatGptService.summarizeFeedbackComments(comments, average, count);
    summaryCache.put(roomId, new CachedSummary(count, summary));
    return summary;
  }
}