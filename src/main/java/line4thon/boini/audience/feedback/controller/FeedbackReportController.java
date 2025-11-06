package line4thon.boini.audience.feedback.controller;

import io.swagger.v3.oas.annotations.Operation;
import line4thon.boini.audience.feedback.service.FeedbackReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import line4thon.boini.audience.feedback.dto.response.FeedbackReportResponse;
import line4thon.boini.global.common.response.BaseResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/report")
public class FeedbackReportController {

  private final FeedbackReportService feedbackQueryService;

  @GetMapping("/{roomId}/feedbacks")
  @Operation(
      summary = "후기 목록 및 통계 조회",
      description = """
          특정 roomId에 해당하는 청중 후기 목록을 조회합니다.
          - 평균 평점(`averageRating`)
          - 후기 개수(`count`)
          - 후기 목록(`feedbacks`)
          - GPT 요약(`summary`)
          """
  )
  public BaseResponse<FeedbackReportResponse> getFeedbacks(@PathVariable String roomId) {
    var result = feedbackQueryService.getFeedbacksByRoom(roomId);
    return BaseResponse.success(result);
  }
}