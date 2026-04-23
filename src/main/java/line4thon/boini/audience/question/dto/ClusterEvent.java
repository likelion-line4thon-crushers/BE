package line4thon.boini.audience.question.dto;

import line4thon.boini.audience.question.dto.response.ClusterReportResponse;

public record ClusterEvent(
    String type,
    ClusterReportResponse data
) {
  public static ClusterEvent updated(ClusterReportResponse r) {
    return new ClusterEvent("CLUSTER_UPDATED", r);
  }
}
