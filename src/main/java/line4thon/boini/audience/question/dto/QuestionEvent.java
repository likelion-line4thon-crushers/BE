package line4thon.boini.audience.question.dto;

import line4thon.boini.audience.question.dto.response.CreateQuestionResponse;

public record QuestionEvent(
    String type,
    CreateQuestionResponse data
) {
  public static QuestionEvent created(CreateQuestionResponse r) {
    return new QuestionEvent("QUESTION_CREATED", r);
  }
}