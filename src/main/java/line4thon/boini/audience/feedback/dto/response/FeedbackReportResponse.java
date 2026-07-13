package line4thon.boini.audience.feedback.dto.response;

import line4thon.boini.audience.feedback.dto.FeedbackItemDto;
import lombok.*;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackReportResponse {
  private double averageRating;
  private int count;
  // AI 요약은 비어 있지 않은 코멘트가 10개를 초과할 때만 활성화됩니다.
  private boolean summarizationEnabled;
  private List<FeedbackItemDto> feedbacks;
  // summarizationEnabled=false 이면 AI 요약 대신 안내 문구가 담깁니다.
  private String summary;
}