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

  /**
   * REST - 슬라이드별 질문 조회 (초기 로딩/무한스크롤)
   * GET /api/rooms/{roomId}/slides/{slide}/questions?limit=50&fromTs=...
   */
  @GetMapping("/rooms/{roomId}/slides/{slide}/questions")
  public BaseResponse<List<CreateQuestionResponse>> list(@PathVariable String roomId,
      @PathVariable int slide,
      @RequestParam(defaultValue = "50") int limit,
      @RequestParam(required = false) Long fromTs) {
    var list = questionService.list(roomId, slide, limit, fromTs);
    return BaseResponse.success("질문 목록을 성공적으로 조회했습니다.", list);
  }
}
