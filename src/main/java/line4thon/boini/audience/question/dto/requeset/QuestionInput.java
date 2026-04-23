package line4thon.boini.audience.question.dto.requeset;

public record QuestionInput(
    String id,
    String content,
    int slide,
    long ts
) {}
