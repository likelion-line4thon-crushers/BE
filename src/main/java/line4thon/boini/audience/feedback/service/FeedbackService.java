package line4thon.boini.audience.feedback.service;

import java.time.Instant;
import line4thon.boini.audience.feedback.dto.request.CreateFeedbackRequest;
import line4thon.boini.audience.feedback.dto.response.CreateFeedbackResponse;
import line4thon.boini.audience.feedback.entity.FeedbackEntity;
import line4thon.boini.audience.feedback.repository.FeedbackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FeedbackService {

   private final FeedbackRepository feedbackRepository;

  public CreateFeedbackResponse write(String roomId, CreateFeedbackRequest req) {

    Instant now = Instant.now();

    FeedbackEntity entity = FeedbackEntity.builder()
        .roomId(roomId)
        .audienceId(req.getAudienceId())
        .rating(req.getRating())
        .comment(req.getComment())
        .createdAt(now)
        .build();

    FeedbackEntity saved = feedbackRepository.save(entity);

    return CreateFeedbackResponse.builder()
        .id(saved.getId())
        .roomId(roomId)
        .audienceId(req.getAudienceId())
        .rating(req.getRating())
        .comment(req.getComment())
        .createdAt(now)
        .build();
  }
}