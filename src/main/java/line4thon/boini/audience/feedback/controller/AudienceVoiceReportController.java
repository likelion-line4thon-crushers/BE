package line4thon.boini.audience.feedback.controller;

import io.swagger.v3.oas.annotations.Operation;
import line4thon.boini.audience.feedback.dto.response.AudienceVoiceResponse;
import line4thon.boini.audience.feedback.service.AudienceVoiceReportService;
import line4thon.boini.global.common.response.BaseResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/report")
@RequiredArgsConstructor
public class AudienceVoiceReportController {

  private final AudienceVoiceReportService audienceVoiceReportService;

  @GetMapping("/{roomId}/audience-voice")
  @Operation(summary = "청중의 목소리 리포트",
      description = "발표자가 만든 질문별 청중 답변 목록과 AI 요약, 세션 만족도 평점을 반환합니다. 질문이 없으면 hasQuestions=false.")
  public BaseResponse<AudienceVoiceResponse> getAudienceVoice(@PathVariable String roomId) {
    return BaseResponse.success(audienceVoiceReportService.getReport(roomId));
  }
}
