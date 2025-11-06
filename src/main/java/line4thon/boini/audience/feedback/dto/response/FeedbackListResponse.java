package line4thon.boini.audience.feedback.dto.response;

import line4thon.boini.audience.feedback.dto.FeedbackItemDto;
import lombok.*;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackListResponse {
  private double averageRating;
  private int count;
  private List<FeedbackItemDto> feedbacks;
}