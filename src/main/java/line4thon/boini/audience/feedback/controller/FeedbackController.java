package line4thon.boini.audience.feedback.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import line4thon.boini.audience.feedback.dto.request.CreateFeedbackRequest;
import line4thon.boini.audience.feedback.dto.response.CreateFeedbackResponse;
import line4thon.boini.audience.feedback.service.FeedbackService;
import line4thon.boini.global.common.response.BaseResponse;

@RestController
@RequestMapping("/api/feedbacks")
@RequiredArgsConstructor
@Validated
public class FeedbackController {

  private final FeedbackService feedbackService;

  @PostMapping("/rooms/{roomId}/feedbacks")
  public BaseResponse<CreateFeedbackResponse> createByPath(
      @PathVariable("roomId") String roomId,
      @Valid @RequestBody CreateFeedbackRequest request
  ) {
    var resp = feedbackService.write(roomId, request);
    return BaseResponse.success(resp);
  }
}
