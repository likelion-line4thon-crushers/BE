package line4thon.boini.audience.question.dto.response;

public record FastApiClusterResponse(
    boolean success,
    String message,
    ClusterReportResponse data
) {}
