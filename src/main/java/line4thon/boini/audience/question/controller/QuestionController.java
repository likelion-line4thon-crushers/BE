package line4thon.boini.audience.question.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import line4thon.boini.audience.question.dto.requeset.CreateQuestionRequest;
import line4thon.boini.audience.question.dto.requeset.QuestionLikeRequest;
import line4thon.boini.audience.question.dto.response.CreateQuestionResponse;
import line4thon.boini.audience.question.service.QuestionService;
import line4thon.boini.global.common.response.BaseResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/questions")
@Tag(name = "청중 질문", description = "청중 실시간 질문 관련 API")
public class QuestionController {

  private static final String LEGACY_ANONYMOUS_SUBJECT = "anon";

  private final QuestionService questionService;

  @MessageMapping("/p/{roomId}/question.create")
  @Operation(summary = "질문 생성 (WebSocket)", description = "STOMP를 통해 실시간 질문을 생성합니다.")
  public void createViaWs(@DestinationVariable String roomId,
      @Valid @Payload CreateQuestionRequest request) {

    questionService.create(roomId, request);
  }

  @MessageMapping("/p/{roomId}/question.like")
  @Operation(summary = "질문 좋아요 토글 (WebSocket)", description = "STOMP를 통해 질문 좋아요 상태를 변경합니다.")
  public void likeViaWs(
      @DestinationVariable String roomId,
      @Valid @Payload QuestionLikeRequest request,
      Principal principal
  ) {
    if (hasVerifiedAudiencePrincipal(principal) && !request.audienceId().equals(principal.getName())) {
      throw new MessagingException("인증된 청중 정보와 요청 청중 정보가 일치하지 않습니다.");
    }
    questionService.updateLike(roomId, request);
  }

  @GetMapping("/rooms/{roomId}")
  @Operation(
      summary = "방의 질문 목록 조회",
      description = "특정 방(roomId)의 질문 목록을 조회합니다. `fromTs`(이후 타임스탬프)와 `slide`(슬라이드 번호)로 필터링할 수 있습니다."
  )
  public BaseResponse<List<CreateQuestionResponse>> listRoom(
      @PathVariable String roomId,
      @RequestParam(required = false) Long fromTs,
      @RequestParam(required = false) Integer slide,
      @RequestParam(required = false) String audienceId
  ) {
    var list = questionService.listRoom(roomId, fromTs, slide, audienceId);
    return BaseResponse.success("질문 목록을 성공적으로 조회했습니다.", list);
  }

  private boolean hasVerifiedAudiencePrincipal(Principal principal) {
    if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
      return false;
    }

    // Existing audience sockets may still carry tokens issued before audienceId
    // was stored as the JWT subject. Let those sessions use the payload ID so
    // like events keep broadcasting until the user rejoins and receives a new token.
    return !LEGACY_ANONYMOUS_SUBJECT.equals(principal.getName());
  }
}
