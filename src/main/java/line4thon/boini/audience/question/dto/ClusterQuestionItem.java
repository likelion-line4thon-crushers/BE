package line4thon.boini.audience.question.dto;

public record ClusterQuestionItem(
    String id,
    String content,
    int slide,
    long ts,
    String status
) {}
