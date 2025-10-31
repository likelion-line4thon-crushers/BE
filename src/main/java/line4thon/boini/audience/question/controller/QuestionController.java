package line4thon.boini.audience.question.controller;

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
public class QuestionController {

  private final QuestionService questionService;

  /**
   * WebSocket(STOMP) - 질문 생성
   * 클라이언트: SEND /app/p/{roomId}/question.create  (payload = CreateQuestionRequest)
   * 구독: /topic/p/{roomId}/public , /topic/p/{roomId}/presenter
   */
  @MessageMapping("/p/{roomId}/question.create")
  public void createViaWs(@DestinationVariable String roomId,
      @Valid @Payload CreateQuestionRequest request) {
    // 저장 + 실시간 브로드캐스트는 Service 가 처리
    questionService.create(roomId, request);
  }

  @GetMapping("/rooms/{roomId}")
  public BaseResponse<List<CreateQuestionResponse>> listRoom(
      @PathVariable String roomId,
      @RequestParam(required = false) Long fromTs,
      @RequestParam(required = false) Integer slide // ← 선택 필터
  ) {
    var list = questionService.listRoom(roomId, fromTs, slide);
    return BaseResponse.success("질문 목록을 성공적으로 조회했습니다.", list);
  }
}
