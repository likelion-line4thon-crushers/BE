package line4thon.boini.audience.feedback.service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import line4thon.boini.audience.feedback.entity.FeedbackAnswerEntity;
import line4thon.boini.audience.feedback.entity.FeedbackEntity;
import line4thon.boini.audience.feedback.entity.FeedbackQuestionEntity;
import line4thon.boini.audience.feedback.repository.FeedbackAnswerRepository;
import line4thon.boini.audience.feedback.repository.FeedbackQuestionRepository;
import line4thon.boini.audience.feedback.repository.FeedbackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AudienceVoiceCsvService {

  private final FeedbackQuestionRepository questionRepository;
  private final FeedbackAnswerRepository answerRepository;
  private final FeedbackRepository feedbackRepository;

  @Transactional(readOnly = true)
  public String buildCsv(String roomId) {
    List<FeedbackQuestionEntity> questions = questionRepository.findByRoomIdOrderByOrderIndexAsc(roomId);
    List<FeedbackEntity> feedbacks = feedbackRepository.findByRoomIdOrderByCreatedAtDesc(roomId);
    List<FeedbackAnswerEntity> answers = answerRepository.findByRoomId(roomId);

    // audienceId -> rating
    Map<String, Integer> ratingByAudience = new LinkedHashMap<>();
    for (FeedbackEntity f : feedbacks) ratingByAudience.putIfAbsent(f.getAudienceId(), f.getRating());

    // audienceId -> (questionId -> answerText)
    Map<String, Map<Long, String>> answersByAudience = new LinkedHashMap<>();
    for (FeedbackAnswerEntity a : answers) {
      answersByAudience
          .computeIfAbsent(a.getAudienceId(), k -> new LinkedHashMap<>())
          .putIfAbsent(a.getQuestionId(), a.getAnswerText());
    }

    // union of audience ids, feedback first (stable), then any answer-only audiences
    Set<String> audienceIds = new LinkedHashSet<>(ratingByAudience.keySet());
    audienceIds.addAll(answersByAudience.keySet());

    StringBuilder sb = new StringBuilder();

    // header
    sb.append(escape("audienceId")).append(',').append(escape("rating"));
    for (FeedbackQuestionEntity q : questions) sb.append(',').append(escape(q.getQuestionText()));
    sb.append("\r\n");

    // rows
    for (String aud : audienceIds) {
      sb.append(escape(aud)).append(',');
      Integer rating = ratingByAudience.get(aud);
      sb.append(rating == null ? "" : String.valueOf(rating));
      Map<Long, String> perQ = answersByAudience.getOrDefault(aud, Map.of());
      for (FeedbackQuestionEntity q : questions) {
        sb.append(',').append(escape(perQ.getOrDefault(q.getId(), "")));
      }
      sb.append("\r\n");
    }

    return sb.toString();
  }

  private String escape(String value) {
    if (value == null) return "";
    String v = value;
    if (!v.isEmpty()) {
      char c = v.charAt(0);
      if (c == '=' || c == '+' || c == '-' || c == '@' || c == '\t' || c == '\r') {
        v = "'" + v;
      }
    }
    boolean needsQuote = v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r");
    String escaped = v.replace("\"", "\"\"");
    return needsQuote ? "\"" + escaped + "\"" : escaped;
  }
}
