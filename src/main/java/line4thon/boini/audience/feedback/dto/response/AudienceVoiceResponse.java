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
  // AI 요약은 모든 문항이 5개를 초과하는 답변을 받았을 때만 활성화됩니다.
  private boolean summarizationEnabled;
  private List<QuestionVoice> questions;

  @Getter
  @Builder
  @AllArgsConstructor
  public static class QuestionVoice {
    private long questionId;
    private int orderIndex;
    private String questionText;
    private List<String> answers;
    private int answerCount;
    // summarizationEnabled=false 이면 null (프론트에서 안내 문구 표시).
    private String summary;
  }
}
