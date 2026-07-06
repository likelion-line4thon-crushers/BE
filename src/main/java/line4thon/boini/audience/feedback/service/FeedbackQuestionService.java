package line4thon.boini.audience.feedback.service;

import java.util.List;
import java.util.stream.IntStream;
import line4thon.boini.audience.feedback.dto.request.SaveFeedbackQuestionsRequest;
import line4thon.boini.audience.feedback.dto.request.SaveFeedbackQuestionsRequest.QuestionItem;
import line4thon.boini.audience.feedback.dto.response.FeedbackQuestionsResponse;
import line4thon.boini.audience.feedback.entity.FeedbackQuestionEntity;
import line4thon.boini.audience.feedback.exception.FeedbackErrorCode;
import line4thon.boini.audience.feedback.repository.FeedbackQuestionRepository;
import line4thon.boini.global.common.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FeedbackQuestionService {

  private static final int MAX_QUESTIONS = 20;
  private static final int MAX_QUESTION_LENGTH = 500;

  private final FeedbackQuestionRepository repository;

  @Transactional(readOnly = true)
  public FeedbackQuestionsResponse list(String roomId) {
    if (roomId == null || roomId.isBlank()) {
      throw new CustomException(FeedbackErrorCode.EMPTY_ROOM_ID);
    }
    return FeedbackQuestionsResponse.of(repository.findByRoomIdOrderByOrderIndexAsc(roomId));
  }

  @Transactional
  public FeedbackQuestionsResponse replace(String roomId, SaveFeedbackQuestionsRequest request) {
    if (roomId == null || roomId.isBlank()) {
      throw new CustomException(FeedbackErrorCode.EMPTY_ROOM_ID);
    }

    List<QuestionItem> incoming = request.getQuestions() == null ? List.of() : request.getQuestions();
    List<QuestionItem> cleaned = incoming.stream()
        .filter(q -> q.getQuestionText() != null && !q.getQuestionText().isBlank())
        .toList();

    if (cleaned.size() > MAX_QUESTIONS) {
      throw new CustomException(FeedbackErrorCode.TOO_MANY_QUESTIONS);
    }

    if (cleaned.stream().anyMatch(q -> q.getQuestionText().trim().length() > MAX_QUESTION_LENGTH)) {
      throw new CustomException(FeedbackErrorCode.QUESTION_TOO_LONG);
    }

    repository.deleteByRoomId(roomId);

    List<FeedbackQuestionEntity> toSave = IntStream.range(0, cleaned.size())
        .mapToObj(i -> FeedbackQuestionEntity.builder()
            .roomId(roomId)
            .orderIndex(i)
            .questionText(cleaned.get(i).getQuestionText().trim())
            .build())
        .toList();

    return FeedbackQuestionsResponse.of(repository.saveAll(toSave));
  }
}
