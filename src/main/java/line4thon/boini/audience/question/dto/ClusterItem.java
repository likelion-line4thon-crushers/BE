package line4thon.boini.audience.question.dto;

import java.util.List;

public record ClusterItem(
    String clusterId,
    String representativeQuestionId,
    String representative,
    int count,
    List<ClusterQuestionItem> questions,
    List<String> questionIds,
    List<Integer> slides,
    List<String> samples
) {}
