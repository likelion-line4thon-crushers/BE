package line4thon.boini.audience.question.dto;

import java.util.List;

public record ClusterItem(
    String representative,
    int count,
    List<String> questionIds,
    List<Integer> slides,
    List<String> samples
) {}
