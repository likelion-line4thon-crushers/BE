package line4thon.boini.audience.feedback.controller;

import io.swagger.v3.oas.annotations.Operation;
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
  @Operation(
      summary = "피드백 등록",
      description = """
          특정 roomId에 해당하는 발표에 대해 청중이 별점과 코멘트를 남깁니다.
          - `roomId`: PathVariable로 전달
          - `audienceId`, `rating`, `comment`는 Request Body로 전달
          - 성공 시 저장된 피드백 정보 반환
          """
  )
  public BaseResponse<CreateFeedbackResponse> createByPath(
      @PathVariable("roomId") String roomId,
      @Valid @RequestBody CreateFeedbackRequest request
  ) {
    var resp = feedbackService.write(roomId, request);
    return BaseResponse.success(resp);
  }
}
