package line4thon.boini.audience.feedback.dto.request;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SaveFeedbackQuestionsRequest {

  private List<QuestionItem> questions;

  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  public static class QuestionItem {
    private int orderIndex;
    private String questionText;
  }
}
