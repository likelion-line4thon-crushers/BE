package line4thon.boini.audience.question.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import line4thon.boini.audience.question.dto.requeset.CreateQuestionRequest;
import line4thon.boini.audience.question.dto.response.CreateQuestionResponse;
import line4thon.boini.audience.question.service.QuestionService;
import line4thon.boini.global.common.response.BaseResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/questions")
@Tag(name = "청중 질문", description = "청중 실시간 질문 관련 API")
public class QuestionController {

  private final QuestionService questionService;

  /**
   * WebSocket(STOMP) - 질문 생성
   * 클라이언트: SEND /app/p/{roomId}/question.create  (payload = CreateQuestionRequest)
   * 구독: /topic/p/{roomId}/public , /topic/p/{roomId}/presenter
   */
  @MessageMapping("/p/{roomId}/question.create")
  @Operation(summary = "질문 생성 (WebSocket)", description = "STOMP를 통해 실시간 질문을 생성합니다.")
  public void createViaWs(@DestinationVariable String roomId,
      @Valid @Payload CreateQuestionRequest request) {
    // 저장 + 실시간 브로드캐스트는 Service 가 처리
    questionService.create(roomId, request);
  }

  @GetMapping("/rooms/{roomId}")
  @Operation(
      summary = "방의 질문 목록 조회",
      description = "특정 방(roomId)의 질문 목록을 조회합니다. `fromTs`(이후 타임스탬프)와 `slide`(슬라이드 번호)로 필터링할 수 있습니다."
  )
  public BaseResponse<List<CreateQuestionResponse>> listRoom(
      @PathVariable String roomId,
      @RequestParam(required = false) Long fromTs,
      @RequestParam(required = false) Integer slide // ← 선택 필터
  ) {
    var list = questionService.listRoom(roomId, fromTs, slide);
    return BaseResponse.success("질문 목록을 성공적으로 조회했습니다.", list);
  }
}
