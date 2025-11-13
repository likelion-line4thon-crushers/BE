package line4thon.boini.presenter.aiReport.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import line4thon.boini.presenter.aiReport.dto.response.ReportResponse;
import line4thon.boini.presenter.room.entity.Report;

public final class ReportMapper {

  private static final ObjectMapper OM = new ObjectMapper();

  private ReportMapper() {}

  private static JsonNode toJsonNode(String json) {
    if (json == null || json.isBlank()) return NullNode.getInstance();
    try {
      return OM.readTree(json);
    } catch (Exception e) {
      return NullNode.getInstance();
    }
  }

  public static ReportResponse toDto(Report e) {
    return ReportResponse.builder()
        .id(e.getId())
        .roomId(e.getRoomId())
        .emojiCount(e.getEmojiCount())
        .questionCount(e.getQuestionCount())
        .attentionSlide(e.getAttentionSlide())
        .top3Question(toJsonNode(e.getTop3Question()))
        .popularEmoji(toJsonNode(e.getPopularEmoji()))
        .popularQuestion(toJsonNode(e.getPopularQuestion()))
        .revisit(toJsonNode(e.getRevisit()))
        .build();
  }
}
