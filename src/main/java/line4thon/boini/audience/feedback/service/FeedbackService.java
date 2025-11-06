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

@Service
@RequiredArgsConstructor
public class FeedbackService {

   private final FeedbackRepository feedbackRepository;

  public CreateFeedbackResponse write(String roomId, CreateFeedbackRequest request) {

    if (roomId == null || roomId.isBlank()) {
      throw new CustomException(FeedbackErrorCode.EMPTY_ROOM_ID);
    }

    if (request.getRating() < 1 || request.getRating() > 5) {
      throw new CustomException(FeedbackErrorCode.INVALID_RATING);
    }

    try {
      Instant now = Instant.now();

      FeedbackEntity entity = FeedbackEntity.builder()
          .roomId(roomId)
          .audienceId(request.getAudienceId())
          .rating(request.getRating())
          .comment(request.getComment())
          .createdAt(now)
          .build();

      FeedbackEntity saved = feedbackRepository.save(entity);

      return CreateFeedbackResponse.builder()
          .id(saved.getId())
          .roomId(roomId)
          .audienceId(request.getAudienceId())
          .rating(request.getRating())
          .comment(request.getComment())
          .createdAt(now)
          .build();

    } catch (Exception e) {
      throw new CustomException(FeedbackErrorCode.SAVE_FAILED);
    }
  }
}