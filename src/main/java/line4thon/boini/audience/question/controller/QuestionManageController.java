package line4thon.boini.audience.question.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import line4thon.boini.audience.question.dto.response.CreateQuestionResponse;
import line4thon.boini.audience.question.service.QuestionManageService;
import line4thon.boini.global.common.response.BaseResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/questions")
@Tag(name = "청중 질문 관리", description = "발표자의 질문 완료/삭제 처리 API")
public class QuestionManageController {

  private final QuestionManageService questionManageService;

  @PatchMapping("/{roomId}/{questionId}/complete")
  @Operation(
      summary = "질문 완료 처리",
      description = "발표자가 질문에 답변 완료 처리합니다. QUESTION_COMPLETED 이벤트가 broadcast됩니다."
  )
  public BaseResponse<String> complete(
      @PathVariable String roomId,
      @PathVariable String questionId
  ) {
    questionManageService.complete(roomId, questionId);
    return BaseResponse.success("질문이 완료 처리되었습니다.");
  }

  @PatchMapping("/{roomId}/{questionId}/delete")
  @Operation(
      summary = "질문 삭제 처리 (soft delete)",
      description = "발표자가 질문을 삭제합니다. 실시간 채팅창에서 즉시 제거되며 QUESTION_DELETED 이벤트가 broadcast됩니다."
  )
  public BaseResponse<String> delete(
      @PathVariable String roomId,
      @PathVariable String questionId
  ) {
    questionManageService.delete(roomId, questionId);
    return BaseResponse.success("질문이 삭제되었습니다.");
  }

  @GetMapping("/rooms/{roomId}/completed")
  @Operation(
      summary = "완료된 질문 목록 조회",
      description = "발표자가 완료 처리한 질문 목록을 반환합니다. ts 오름차순 정렬."
  )
  public BaseResponse<List<CreateQuestionResponse>> listCompleted(
      @PathVariable String roomId
  ) {
    return BaseResponse.success("완료된 질문 목록을 조회했습니다.", questionManageService.listCompleted(roomId));
  }
}
