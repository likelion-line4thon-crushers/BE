package line4thon.boini.presenter.aiReport.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ReportResponse {
  private Long id;
  private String roomId;
  private Integer emojiCount;
  private Integer questionCount;
  private Integer attentionSlide;

  private JsonNode top3Question;    // 문자열 → JSON 파싱
  private JsonNode popularEmoji;
  private JsonNode popularQuestion;
  private JsonNode revisit;
}
