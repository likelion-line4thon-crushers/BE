package line4thon.boini.audience.feedback.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import line4thon.boini.audience.feedback.dto.response.AudienceVoiceResponse;
import line4thon.boini.audience.feedback.dto.response.AudienceVoiceResponse.QuestionVoice;
import line4thon.boini.audience.feedback.entity.FeedbackAnswerEntity;
import line4thon.boini.audience.feedback.entity.FeedbackEntity;
import line4thon.boini.audience.feedback.entity.FeedbackQuestionEntity;
import line4thon.boini.audience.feedback.exception.FeedbackErrorCode;
import line4thon.boini.audience.feedback.repository.FeedbackAnswerRepository;
import line4thon.boini.audience.feedback.repository.FeedbackQuestionRepository;
import line4thon.boini.audience.feedback.repository.FeedbackRepository;
import line4thon.boini.global.common.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AudienceVoiceReportService {

  // AI 요약 활성화 임계값: 모든 문항이 이 개수를 "초과"하는 답변을 받아야 함.
  private static final int SUMMARY_THRESHOLD = 5;

  private final FeedbackQuestionRepository questionRepository;
  private final FeedbackAnswerRepository answerRepository;
  private final FeedbackRepository feedbackRepository;
  private final ChatGptService chatGptService;

  // 문항별 요약 캐시: key = "roomId:questionId". 답변 수가 증가했을 때만 재생성한다.
  private final Map<String, CachedSummary> summaryCache = new ConcurrentHashMap<>();

  private record CachedSummary(int answerCount, String summary) {}

  @Transactional(readOnly = true)
  public AudienceVoiceResponse getReport(String roomId) {
    if (roomId == null || roomId.isBlank()) {
      throw new CustomException(FeedbackErrorCode.EMPTY_ROOM_ID);
    }

    double averageRating = averageRating(roomId);

    List<FeedbackQuestionEntity> questions = questionRepository.findByRoomIdOrderByOrderIndexAsc(roomId);
    if (questions.isEmpty()) {
      return AudienceVoiceResponse.builder()
          .averageRating(averageRating)
          .hasQuestions(false)
          .summarizationEnabled(false)
          .questions(List.of())
          .build();
    }

    Map<Long, List<String>> answersByQuestion = answerRepository.findByRoomId(roomId).stream()
        .filter(a -> a.getAnswerText() != null && !a.getAnswerText().isBlank())
        .collect(Collectors.groupingBy(
            FeedbackAnswerEntity::getQuestionId,
            Collectors.mapping(FeedbackAnswerEntity::getAnswerText, Collectors.toList())));

    // 모든 문항이 5개를 "초과"하는 답변을 받았을 때만 AI 요약 활성화.
    boolean summarizationEnabled = questions.stream()
        .allMatch(q -> answersByQuestion.getOrDefault(q.getId(), List.of()).size() > SUMMARY_THRESHOLD);

    List<QuestionVoice> voices = questions.stream()
        .map(q -> {
          List<String> answers = answersByQuestion.getOrDefault(q.getId(), List.of());
          String summary =
              summarizationEnabled ? summaryFor(roomId, q.getId(), q.getQuestionText(), answers) : null;
          return QuestionVoice.builder()
              .questionId(q.getId())
              .orderIndex(q.getOrderIndex())
              .questionText(q.getQuestionText())
              .answers(answers)
              .answerCount(answers.size())
              .summary(summary)
              .build();
        })
        .toList();

    return AudienceVoiceResponse.builder()
        .averageRating(averageRating)
        .hasQuestions(true)
        .summarizationEnabled(summarizationEnabled)
        .questions(voices)
        .build();
  }

  // 답변 수가 캐시된 값과 같으면(=새 답변 없음) 캐시를 재사용하고, 늘어났을 때만 재요약한다.
  private String summaryFor(String roomId, long questionId, String questionText, List<String> answers) {
    String key = roomId + ":" + questionId;
    int count = answers.size();
    CachedSummary cached = summaryCache.get(key);
    if (cached != null && cached.answerCount() == count) {
      return cached.summary();
    }
    String summary = chatGptService.summarizeQuestionAnswers(questionText, answers);
    summaryCache.put(key, new CachedSummary(count, summary));
    return summary;
  }

  private double averageRating(String roomId) {
    List<FeedbackEntity> feedbacks = feedbackRepository.findByRoomIdOrderByCreatedAtDesc(roomId);
    if (feedbacks.isEmpty()) return 0.0;
    double avg = feedbacks.stream().mapToInt(FeedbackEntity::getRating).average().orElse(0.0);
    return Math.round(avg * 10.0) / 10.0;
  }
}
