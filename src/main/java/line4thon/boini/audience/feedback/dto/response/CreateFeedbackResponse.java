package line4thon.boini.audience.feedback.dto.response;

import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CreateFeedbackResponse {
  private Long id;
  private String roomId;
  private String audienceId;
  private int rating;
  private String comment;
  private Instant createdAt;
}