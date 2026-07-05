package line4thon.boini.audience.question.dto.response;

public record CreateQuestionResponse(
    String id,
    String roomId,
    Integer slide,
    String audienceId,
    String content,
    long ts,
    int likeCount,
    boolean likedByMe
) {
  public CreateQuestionResponse(
      String id,
      String roomId,
      Integer slide,
      String audienceId,
      String content,
      long ts
  ) {
    this(id, roomId, slide, audienceId, content, ts, 0, false);
  }

  public CreateQuestionResponse(
      String id,
      String roomId,
      Integer slide,
      String audienceId,
      String content,
      long ts,
      int likeCount
  ) {
    this(id, roomId, slide, audienceId, content, ts, likeCount, false);
  }
}
