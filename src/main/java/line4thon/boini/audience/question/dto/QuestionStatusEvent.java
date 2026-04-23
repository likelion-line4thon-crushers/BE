package line4thon.boini.audience.question.dto;

public record QuestionStatusEvent(
    String type,
    String questionId
) {
  public static QuestionStatusEvent completed(String questionId) {
    return new QuestionStatusEvent("QUESTION_COMPLETED", questionId);
  }

  public static QuestionStatusEvent deleted(String questionId) {
    return new QuestionStatusEvent("QUESTION_DELETED", questionId);
  }
}
