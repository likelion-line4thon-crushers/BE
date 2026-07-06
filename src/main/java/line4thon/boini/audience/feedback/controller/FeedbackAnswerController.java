package line4thon.boini.audience.feedback.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import line4thon.boini.audience.feedback.dto.request.SubmitFeedbackAnswersRequest;
import line4thon.boini.audience.feedback.dto.response.FeedbackAnswersResponse;
import line4thon.boini.audience.feedback.service.FeedbackAnswerService;
import line4thon.boini.global.common.response.BaseResponse;
import line4thon.boini.global.jwt.util.AudienceTokenResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rooms/{roomId}/feedback-answers")
@RequiredArgsConstructor
public class FeedbackAnswerController {

  private final FeedbackAnswerService feedbackAnswerService;
  private final AudienceTokenResolver audienceTokenResolver;

  @PostMapping
  @Operation(summary = "세션 후기 답변 제출",
      description = "청중이 발표자가 만든 후기 질문에 대한 답변을 제출합니다. 같은 청중이 다시 제출하면 이전 답변을 교체합니다.")
  public BaseResponse<FeedbackAnswersResponse> submit(
      @PathVariable("roomId") String roomId,
      @RequestBody SubmitFeedbackAnswersRequest request,
      HttpServletRequest httpRequest) {
    // 신원은 요청 본문이 아니라 입장 시 발급된 토큰에서 가져온다.
    String audienceId = audienceTokenResolver.resolveAudienceId(httpRequest, roomId);
    return BaseResponse.success(feedbackAnswerService.submit(roomId, audienceId, request));
  }
}
