package line4thon.boini.audience.feedback.service;

import java.time.Instant;
import line4thon.boini.audience.feedback.dto.request.CreateFeedbackRequest;
import line4thon.boini.audience.feedback.dto.response.CreateFeedbackResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FeedbackService {

  // private final FeedbackRepository feedbackRepository; // 실제 저장시 주입

  public CreateFeedbackResponse write(String roomId, CreateFeedbackRequest req) {

    long id = System.currentTimeMillis();
    Instant now = Instant.now();

    // FeedbackEntity entity = FeedbackEntity.builder()
    //     .id(id)
    //     .roomId(roomId)
    //     .audienceId(req.getAudienceId())
    //     .rating(req.getRating())
    //     .comment(req.getComment())
    //     .createdAt(now)
    //     .build();
    // feedbackRepository.save(entity);

    return CreateFeedbackResponse.builder()
        .id(id)
        .roomId(roomId)
        .audienceId(req.getAudienceId())
        .rating(req.getRating())
        .comment(req.getComment())
        .createdAt(now)
        .build();
  }
}