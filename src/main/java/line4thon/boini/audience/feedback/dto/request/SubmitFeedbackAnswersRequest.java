package line4thon.boini.audience.feedback.dto.request;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SubmitFeedbackAnswersRequest {

  private String audienceId;
  private List<AnswerItem> answers;

  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  public static class AnswerItem {
    private long questionId;
    private String answerText;
  }
}
