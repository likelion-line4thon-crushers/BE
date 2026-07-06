package line4thon.boini.audience.feedback.controller;

import io.swagger.v3.oas.annotations.Operation;
import line4thon.boini.audience.feedback.dto.request.SaveFeedbackQuestionsRequest;
import line4thon.boini.audience.feedback.dto.response.FeedbackQuestionsResponse;
import line4thon.boini.audience.feedback.service.FeedbackQuestionService;
import line4thon.boini.global.common.response.BaseResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rooms/{roomId}/feedback-questions")
@RequiredArgsConstructor
public class FeedbackQuestionController {

  private final FeedbackQuestionService feedbackQuestionService;

  @GetMapping
  @Operation(summary = "세션 후기 질문 조회", description = "roomId의 발표자 작성 후기 질문 목록을 orderIndex 순으로 반환합니다.")
  public BaseResponse<FeedbackQuestionsResponse> list(@PathVariable("roomId") String roomId) {
    return BaseResponse.success(feedbackQuestionService.list(roomId));
  }

  @PutMapping
  @Operation(summary = "세션 후기 질문 저장", description = "roomId의 후기 질문 전체를 교체 저장합니다. 빈 문항은 무시되며 최대 20개까지 허용됩니다.")
  public BaseResponse<FeedbackQuestionsResponse> replace(
      @PathVariable("roomId") String roomId,
      @RequestBody SaveFeedbackQuestionsRequest request) {
    return BaseResponse.success(feedbackQuestionService.replace(roomId, request));
  }
}
