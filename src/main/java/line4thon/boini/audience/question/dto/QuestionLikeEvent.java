package line4thon.boini.audience.question.dto;

public record QuestionLikeEvent(
    String type,
    String questionId,
    String audienceId,
    boolean liked,
    int likeCount
) {
  public static QuestionLikeEvent updated(
      String questionId,
      String audienceId,
      boolean liked,
      int likeCount
  ) {
    return new QuestionLikeEvent(
        "QUESTION_LIKE_UPDATED",
        questionId,
        audienceId,
        liked,
        likeCount
    );
  }
}
