package line4thon.boini.audience.feedback.dto.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class AudienceVoiceResponse {

  private double averageRating;
  private boolean hasQuestions;
  private List<QuestionVoice> questions;

  @Getter
  @Builder
  @AllArgsConstructor
  public static class QuestionVoice {
    private long questionId;
    private int orderIndex;
    private String questionText;
    private List<String> answers;
    private String summary;
  }
}
