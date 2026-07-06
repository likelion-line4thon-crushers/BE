package line4thon.boini.audience.feedback.dto.response;

import java.util.List;
import line4thon.boini.audience.feedback.entity.FeedbackAnswerEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class FeedbackAnswersResponse {

  private List<Item> answers;

  @Getter
  @Builder
  @AllArgsConstructor
  public static class Item {
    private Long id;
    private long questionId;
    private String answerText;
  }

  public static FeedbackAnswersResponse of(List<FeedbackAnswerEntity> entities) {
    List<Item> items = entities.stream()
        .map(e -> Item.builder()
            .id(e.getId())
            .questionId(e.getQuestionId())
            .answerText(e.getAnswerText())
            .build())
        .toList();
    return FeedbackAnswersResponse.builder().answers(items).build();
  }
}
