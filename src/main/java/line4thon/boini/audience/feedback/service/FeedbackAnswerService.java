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

  /**
   * @param audienceId 인증된 청중 토큰에서 추출한 신원 (요청 본문 값이 아님)
   */
  @Transactional
  public FeedbackAnswersResponse submit(String roomId, String audienceId, SubmitFeedbackAnswersRequest request) {
    if (roomId == null || roomId.isBlank()) {
      throw new CustomException(FeedbackErrorCode.EMPTY_ROOM_ID);
    }
    if (audienceId == null || audienceId.isBlank()) {
      throw new CustomException(FeedbackErrorCode.EMPTY_ROOM_ID);
    }

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
