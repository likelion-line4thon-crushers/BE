package line4thon.boini.audience.feedback.service;

import java.time.Instant;
import line4thon.boini.audience.feedback.dto.request.CreateFeedbackRequest;
import line4thon.boini.audience.feedback.dto.response.CreateFeedbackResponse;
import line4thon.boini.audience.feedback.entity.FeedbackEntity;
import line4thon.boini.audience.feedback.exception.FeedbackErrorCode;
import line4thon.boini.audience.feedback.repository.FeedbackRepository;
import line4thon.boini.global.common.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FeedbackService {

   private final FeedbackRepository feedbackRepository;

  /**
   * @param audienceId 인증된 청중 토큰에서 추출한 신원 (요청 본문 값이 아님)
   */
  @Transactional
  public CreateFeedbackResponse write(String roomId, String audienceId, CreateFeedbackRequest request) {

    if (roomId == null || roomId.isBlank()) {
      throw new CustomException(FeedbackErrorCode.EMPTY_ROOM_ID);
    }

    if (request.getRating() < 1 || request.getRating() > 5) {
      throw new CustomException(FeedbackErrorCode.INVALID_RATING);
    }

    try {
      Instant now = Instant.now();

      // 한 청중은 방당 하나의 평가만 남긴다. 재제출 시 이전 평가를 교체한다.
      // unique 제약과 충돌하지 않도록 INSERT 전에 DELETE 를 먼저 flush 한다.
      feedbackRepository.deleteByRoomIdAndAudienceId(roomId, audienceId);
      feedbackRepository.flush();

      FeedbackEntity entity = FeedbackEntity.builder()
          .roomId(roomId)
          .audienceId(audienceId)
          .rating(request.getRating())
          .comment(request.getComment())
          .createdAt(now)
          .build();

      FeedbackEntity saved = feedbackRepository.save(entity);

      return CreateFeedbackResponse.builder()
          .id(saved.getId())
          .roomId(roomId)
          .audienceId(audienceId)
          .rating(request.getRating())
          .comment(request.getComment())
          .createdAt(now)
          .build();

    } catch (CustomException e) {
      throw e;
    } catch (Exception e) {
      throw new CustomException(FeedbackErrorCode.SAVE_FAILED);
    }
  }
}