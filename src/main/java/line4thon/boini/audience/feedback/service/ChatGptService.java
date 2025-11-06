package line4thon.boini.audience.feedback.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class ChatGptService {

  @Value("${OPENAI_API_KEY}")
  private String apiKey;

  private final RestTemplate restTemplate = new RestTemplate();
  private final ObjectMapper mapper = new ObjectMapper();

  public String summarizeFeedbackComments(List<String> comments, double average, int count) {
    if (comments == null || comments.isEmpty()) {
      return "아직 청중 후기가 없습니다.";
    }

    String joined = String.join("\n- ", comments);
    String prompt = """
        다음은 발표에 대한 청중들의 후기입니다.
        평균 평점은 %.1f점이고, 총 %d개의 후기가 있습니다.

        후기 목록:
        - %s

        위 내용을 최대 3문장으로 간결하게 요약해주세요.
        발표의 전반적 만족도가 있으면 포함해주세요.
        """.formatted(average, count, joined);

    try {
      Map<String, Object> body = Map.of(
          "model", "gpt-4o",
          "messages", List.of(Map.of("role", "user", "content", prompt))
      );

      HttpHeaders headers = new HttpHeaders();
      headers.setBearerAuth(apiKey);
      headers.setContentType(MediaType.APPLICATION_JSON);

      ResponseEntity<String> resp = restTemplate.postForEntity(
          "https://api.openai.com/v1/chat/completions",
          new HttpEntity<>(body, headers),
          String.class
      );

      Map<?, ?> json = mapper.readValue(resp.getBody(), Map.class);
      var choices = (List<Map<?, ?>>) json.get("choices");
      if (choices == null || choices.isEmpty()) return "(요약 생성 실패)";
      Map<?, ?> message = (Map<?, ?>) choices.get(0).get("message");
      return (String) message.get("content");

    } catch (Exception e) {
      e.printStackTrace();
      return "(요약 생성 중 오류)";
    }
  }
}