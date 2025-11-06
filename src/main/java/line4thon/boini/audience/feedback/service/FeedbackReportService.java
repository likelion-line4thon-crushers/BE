package line4thon.boini.audience.feedback.service;

import line4thon.boini.audience.feedback.dto.FeedbackItemDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import line4thon.boini.audience.feedback.dto.response.*;
import line4thon.boini.audience.feedback.entity.FeedbackEntity;
import line4thon.boini.audience.feedback.repository.FeedbackRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FeedbackReportService {

  private final FeedbackRepository feedbackRepository;
  private final ChatGptService chatGptService;


  public FeedbackReportResponse getFeedbacksByRoom(String roomId) {
    List<FeedbackEntity> list = feedbackRepository.findByRoomIdOrderByCreatedAtDesc(roomId);

    if (list.isEmpty()) {
      return FeedbackReportResponse.builder()
          .averageRating(0)
          .count(0)
          .feedbacks(List.of())
          .build();
    }

    double avg = list.stream()
        .mapToInt(FeedbackEntity::getRating)
        .average()
        .orElse(0.0);

    List<String> comments = list.stream()
        .map(FeedbackEntity::getComment)
        .filter(c -> c != null && !c.isBlank())
        .toList();

    String summary = chatGptService.summarizeFeedbackComments(comments, avg, comments.size());

    List<FeedbackItemDto> items = list.stream()
        .map(f -> FeedbackItemDto.builder()
            .audienceId(f.getAudienceId())
            .rating(f.getRating())
            .comment(f.getComment())
            .createdAt(f.getCreatedAt())
            .build())
        .toList();

    return FeedbackReportResponse.builder()
        .averageRating(Math.round(avg * 10.0) / 10.0)
        .count(items.size())
        .summary(summary)
        .feedbacks(items)
        .build();
  }
}
