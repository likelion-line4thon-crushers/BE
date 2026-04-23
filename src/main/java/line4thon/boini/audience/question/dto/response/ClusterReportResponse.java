package line4thon.boini.audience.question.dto.response;

import line4thon.boini.audience.question.dto.ClusterItem;

import java.util.List;

public record ClusterReportResponse(
    String roomId,
    int totalQuestions,
    int uniqueGroups,
    List<ClusterItem> clusters
) {}
