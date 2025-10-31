package line4thon.boini.audience.question.dto.response;

public record CreateQuestionResponse(
    String id,
    String roomId,
    Integer slide,
    String audienceId,
    String content,
    long ts
) {}
