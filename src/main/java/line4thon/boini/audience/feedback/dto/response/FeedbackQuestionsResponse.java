package line4thon.boini.audience.feedback.dto.response;

import java.util.List;
import line4thon.boini.audience.feedback.entity.FeedbackQuestionEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class FeedbackQuestionsResponse {

  private List<Item> questions;

  @Getter
  @Builder
  @AllArgsConstructor
  public static class Item {
    private Long id;
    private int orderIndex;
    private String questionText;
  }

  public static FeedbackQuestionsResponse of(List<FeedbackQuestionEntity> entities) {
    List<Item> items = entities.stream()
        .map(e -> Item.builder()
            .id(e.getId())
            .orderIndex(e.getOrderIndex())
            .questionText(e.getQuestionText())
            .build())
        .toList();
    return FeedbackQuestionsResponse.builder().questions(items).build();
  }
}
