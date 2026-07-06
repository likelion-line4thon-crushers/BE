package line4thon.boini.audience.feedback.service;

import java.util.List;
import java.util.Map;
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

  private final FeedbackQuestionRepository questionRepository;
  private final FeedbackAnswerRepository answerRepository;
  private final FeedbackRepository feedbackRepository;
  private final ChatGptService chatGptService;

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
          .questions(List.of())
          .build();
    }

    Map<Long, List<String>> answersByQuestion = answerRepository.findByRoomId(roomId).stream()
        .filter(a -> a.getAnswerText() != null && !a.getAnswerText().isBlank())
        .collect(Collectors.groupingBy(
            FeedbackAnswerEntity::getQuestionId,
            Collectors.mapping(FeedbackAnswerEntity::getAnswerText, Collectors.toList())));

    List<QuestionVoice> voices = questions.stream()
        .map(q -> {
          List<String> answers = answersByQuestion.getOrDefault(q.getId(), List.of());
          String summary = chatGptService.summarizeQuestionAnswers(q.getQuestionText(), answers);
          return QuestionVoice.builder()
              .questionId(q.getId())
              .orderIndex(q.getOrderIndex())
              .questionText(q.getQuestionText())
              .answers(answers)
              .summary(summary)
              .build();
        })
        .toList();

    return AudienceVoiceResponse.builder()
        .averageRating(averageRating)
        .hasQuestions(true)
        .questions(voices)
        .build();
  }

  private double averageRating(String roomId) {
    List<FeedbackEntity> feedbacks = feedbackRepository.findByRoomIdOrderByCreatedAtDesc(roomId);
    if (feedbacks.isEmpty()) return 0.0;
    double avg = feedbacks.stream().mapToInt(FeedbackEntity::getRating).average().orElse(0.0);
    return Math.round(avg * 10.0) / 10.0;
  }
}
