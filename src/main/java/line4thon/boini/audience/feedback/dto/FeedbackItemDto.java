package line4thon.boini.audience.feedback.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackItemDto {
  private String audienceId;
  private int rating;
  private String comment;
  private Instant createdAt;
}
