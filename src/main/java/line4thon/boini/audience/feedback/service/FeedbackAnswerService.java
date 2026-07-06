package line4thon.boini.audience.feedback.service;

import java.time.Instant;
import java.util.List;
import line4thon.boini.audience.feedback.dto.request.SubmitFeedbackAnswersRequest;
import line4thon.boini.audience.feedback.dto.request.SubmitFeedbackAnswersRequest.AnswerItem;
import line4thon.boini.audience.feedback.dto.response.FeedbackAnswersResponse;
import line4thon.boini.audience.feedback.entity.FeedbackAnswerEntity;
import line4thon.boini.audience.feedback.exception.FeedbackErrorCode;
import line4thon.boini.audience.feedback.repository.FeedbackAnswerRepository;
import line4thon.boini.global.common.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FeedbackAnswerService {

  private final FeedbackAnswerRepository repository;

  @Transactional
  public FeedbackAnswersResponse submit(String roomId, SubmitFeedbackAnswersRequest request) {
    if (roomId == null || roomId.isBlank()) {
      throw new CustomException(FeedbackErrorCode.EMPTY_ROOM_ID);
    }
    if (request.getAudienceId() == null || request.getAudienceId().isBlank()) {
      throw new CustomException(FeedbackErrorCode.EMPTY_ROOM_ID);
    }

    String audienceId = request.getAudienceId();
    repository.deleteByRoomIdAndAudienceId(roomId, audienceId);

    List<AnswerItem> incoming = request.getAnswers() == null ? List.of() : request.getAnswers();
    Instant now = Instant.now();
    List<FeedbackAnswerEntity> toSave = incoming.stream()
        .map(a -> FeedbackAnswerEntity.builder()
            .roomId(roomId)
            .audienceId(audienceId)
            .questionId(a.getQuestionId())
            .answerText(a.getAnswerText())
            .createdAt(now)
            .build())
        .toList();

    return FeedbackAnswersResponse.of(repository.saveAll(toSave));
  }
}
