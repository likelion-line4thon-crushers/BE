package line4thon.boini.presenter.room.contorller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import line4thon.boini.audience.feedback.entity.FeedbackEntity;
import line4thon.boini.audience.feedback.repository.FeedbackRepository;
import line4thon.boini.audience.room.dto.request.LeaveRoomRequest;
import line4thon.boini.audience.room.dto.response.JoinResponse;
import line4thon.boini.audience.room.dto.response.LeaveRoomResponse;
import line4thon.boini.audience.room.service.AudienceAuthService;
import line4thon.boini.global.WsUrlResolver;
import line4thon.boini.global.common.exception.CustomException;
import line4thon.boini.global.common.exception.GlobalErrorCode;
import line4thon.boini.global.common.response.BaseResponse;
import line4thon.boini.global.config.AppProperties;
import line4thon.boini.global.jwt.service.JwtService;
import line4thon.boini.global.websocket.JwtHandshakeInterceptor;
import line4thon.boini.presenter.aiReport.exception.ReportErrorCode;
import line4thon.boini.presenter.page.service.PageService;
import line4thon.boini.presenter.room.dto.response.CreateRoomResponse;
import line4thon.boini.presenter.room.dto.response.TokenResponse;
import line4thon.boini.presenter.room.entity.SessionStatus;
import line4thon.boini.presenter.room.service.CodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import line4thon.boini.presenter.room.dto.request.CreateRoomRequest;
import line4thon.boini.presenter.room.dto.request.RefreshPresenterTokenRequest;
import line4thon.boini.presenter.room.service.PresenterAuthService;
import line4thon.boini.presenter.room.service.RoomService;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
@Tag(name = "Room", description = "발표자/청중 방 생성 및 입장 관련 API")
public class RoomController {

  private final RoomService roomService;
  private final PresenterAuthService presenterAuth;
  private final CodeService codeService;
  private final AudienceAuthService audienceAuth;
  private final PageService pageService;
  private final JwtHandshakeInterceptor jwtHandshakeInterceptor;
  private final JwtService jwtService;
  private final SimpMessagingTemplate messagingTemplate;
  private final FeedbackRepository feedbackRepository;
  private final S3Client s3Client;
  private final AppProperties props;

  @Autowired
  private RedisTemplate<String, String> redisTemplate;

  @PostMapping
  @Operation(
      summary = "발표자 방 생성",
      description = "발표자가 새로운 방을 생성합니다."
  )
  public BaseResponse<CreateRoomResponse> create(@Valid @RequestBody CreateRoomRequest request, HttpServletRequest http) {
    String wsUrl = WsUrlResolver.resolve(http);
    var response = roomService.createRoom(request,wsUrl);
    return BaseResponse.success(response);
  }

  @PostMapping("/{roomId}/presenter-token:refresh")
  @Operation(
      summary = "발표자 토큰 재발급",
      description = """
          기존 발표자 키(`presenterKey`)를 사용하여 새 발표자 토큰(`presenterToken`)을 재발급합니다.  
          발표자가 세션을 갱신할 때 사용됩니다.
          """
  )
  public BaseResponse<TokenResponse> refreshToken(@PathVariable String roomId,
      @RequestBody RefreshPresenterTokenRequest request) {
    String newToken = presenterAuth.refreshPresenterToken(roomId, request.getPresenterKey());
    return BaseResponse.success(new TokenResponse(newToken));
  }

  @GetMapping("/join/{code}")
  @Operation(
      summary = "청중 방 입장",
      description = """
          초대 코드(`code`)를 사용해 청중이 방에 입장합니다.  
          유효한 코드만 허용되며, 응답으로는 `roomId`, `audienceId`, `audienceToken`이 발급됩니다.
          """
  )
  public BaseResponse<JoinResponse> joinByPath(@PathVariable("code") String code) {
    String roomId = codeService.resolveRoomIdByCodeOrThrow(code);
    var issued = audienceAuth.issueAudienceToken(roomId);

    String key5 = "room:" + roomId + ":presenterPage";
    String presenterPage = redisTemplate.opsForValue().get(key5);

    // 누적 입장 수(리포트용): 최초 입장하는 audienceId 만 1회 카운트한다.
    // presence(:audience:online)와 분리해 관리 — 현재 접속 수는 WS CONNECT/DISCONNECT 가 단독으로 다룬다.
    String enteredKey = "room:" + roomId + ":audience:entered";
    Long firstEnter = redisTemplate.opsForSet().add(enteredKey, issued.audienceId());
    redisTemplate.expire(enteredKey, java.time.Duration.ofSeconds(props.getRoom().getTtlSeconds()));
    if (firstEnter != null && firstEnter >= 1) {
      redisTemplate.opsForValue().increment("room:" + roomId + ":enterAudienceCount");
    }

    // 청중 수(:audience:online)는 소켓 연결 시점에 CONNECT 이벤트가 추가한다.
    // join 시점엔 아직 소켓이 없으므로 online 에 넣지 않는다(소켓 미연결로 인한 카운트 누수 방지).

    String key2 = "room:" + roomId + ":slide:" + presenterPage;
    redisTemplate.opsForSet().add(key2, issued.audienceId());

    String key3= "room:" + roomId + ":deckId";
    String deckId = redisTemplate.opsForValue().get(key3);

    String totalpage = redisTemplate.opsForValue().get("room:" + roomId + ":totalPage");
    if (totalpage == null) {
      throw new CustomException(ReportErrorCode.TOTAL_PAGE_NULL);
    }
    int totalPages = Integer.parseInt(totalpage);

    String maxPage = redisTemplate.opsForValue().get("room:" + roomId + ":maxSlide");
    String sticker = redisTemplate.opsForValue().get("room:" + roomId + ":option:sticker");
    String question = redisTemplate.opsForValue().get("room:" + roomId + ":option:question");
    String feedback = redisTemplate.opsForValue().get("room:" + roomId + ":option:feedback");
    String slideUnlock = redisTemplate.opsForValue().get("room:" + roomId + ":option:slideUnlock");

    String sessionStatus = roomService.getSessionStatus(roomId).name();

    return BaseResponse.success(new JoinResponse(
        roomId,
        code,
        issued.audienceId(),
        issued.audienceToken(),
        deckId,
        totalPages,
        presenterPage,
        sessionStatus,
        maxPage,
        sticker,
        question,
        feedback,
        slideUnlock
    ));
  }

  @PostMapping("/leave/{roomId}")
  @Operation(
          summary = "청중 방 퇴장",
          description = """
          `roomId`, `audienceId`, `audienceToken`으로 해당 청중을 방에서 퇴장시킵니다.
          """
  )
  public BaseResponse<LeaveRoomResponse> leaveRoom(@PathVariable("roomId") String roomId, @RequestBody LeaveRoomRequest request) {
    // leaveRoom 내부(forceRemoveAudience)에서 온라인/슬라이드 집합 정리 후 청중 수를 브로드캐스트한다
    return roomService.leaveRoom(roomId, request);
  }

  @DeleteMapping("/close/{roomId}")
  @Operation(
          summary = "발표자 세션 완전 종료(AI리포트 페이지 종료)",
          description = """
          `roomId로 세션을 종료합니다. 종료 이벤트를 브로드캐스트한 뒤, 방 관련 Redis 키를 모두 삭제합니다.
          """
  )
  public BaseResponse closeRoom(@PathVariable("roomId") String roomId) {

    roomService.closeRoom(roomId);

    return BaseResponse.success("방을 성공적으로 삭제하였습니다.");
  }

  @GetMapping("/onlineAudience/{roomId}")
  @Operation(
          summary = "현재 청중 수 반환",
          description = """
          청중 수 반환
          """
  )
  public Long onlineAudience(@PathVariable("roomId") String roomId) {

    String key = "room:"+roomId+":audience:online";

    return redisTemplate.opsForSet().size(key);
  }

  @PostMapping("/{roomId}/session/start")
  @Operation(
      summary = "세션 시작",
      description = """
        발표자가 세션을 시작합니다.  
        세션 상태가 `waiting` → `live`로 전환되고,  
        모든 청중에게 WebSocket 브로드캐스트로 알림이 전송됩니다.
        """
  )
  public BaseResponse<Map<String, String>> startSession(@PathVariable String roomId) {
    roomService.setSessionStatus(roomId, SessionStatus.live);
    return BaseResponse.success(Map.of(
        "roomId", roomId,
        "status", "live"
    ));
  }

  @GetMapping("/{roomId}/session/status")
  @Operation(
      summary = "세션 상태 조회",
      description = """
        해당 방의 현재 세션 상태(`waiting` / `live` / `ended`)를 반환합니다.
        발표자가 준비 페이지로 (재)진입했을 때 이미 시작된 세션인지 판별하는 데 사용합니다.
        """
  )
  public BaseResponse<Map<String, String>> sessionStatus(@PathVariable String roomId) {
    String status = roomService.getSessionStatus(roomId).name();
    return BaseResponse.success(Map.of(
        "roomId", roomId,
        "status", status
    ));
  }

  @PatchMapping("/{roomId}/pdf-download-policy")
  @Operation(
      summary = "PDF 다운로드 허용 정책 변경",
      description = """
        발표자가 준비 화면에서 세션 종료 후 청중의 PDF 다운로드 허용 여부를 설정합니다.
        """
  )
  public BaseResponse<Map<String, Boolean>> updatePdfDownloadPolicy(
      @PathVariable String roomId,
      @RequestBody Map<String, Boolean> request
  ) {
    boolean enabled = Boolean.TRUE.equals(request.get("enabled"));
    return BaseResponse.success(Map.of(
        "enabled", roomService.setPdfDownloadPolicy(roomId, enabled)
    ));
  }

  @GetMapping("/{roomId}/pdf-download/availability")
  @Operation(
      summary = "청중별 PDF 다운로드 가능 여부 조회",
      description = """
        세션 종료, 발표자 허용 정책, 청중의 별점 및 주관식 피드백 제출 여부를 모두 확인합니다.
        """
  )
  public BaseResponse<Map<String, Object>> pdfDownloadAvailability(
      @PathVariable String roomId,
      @RequestParam String audienceId
  ) {
    boolean enabled = roomService.isPdfDownloadEnabled(roomId);
    boolean sessionEnded = roomService.getSessionStatus(roomId) == SessionStatus.ended;
    boolean submittedFeedback = hasSubmittedWrittenFeedback(roomId, audienceId);
    boolean hasFile = hasDownloadablePdf(roomId);

    return BaseResponse.success(Map.of(
        "enabled", enabled,
        "sessionEnded", sessionEnded,
        "submittedFeedback", submittedFeedback,
        "hasFile", hasFile,
        "canDownload", enabled && sessionEnded && submittedFeedback && hasFile
    ));
  }

  @GetMapping("/{roomId}/pdf-download")
  @Operation(
      summary = "PDF 다운로드",
      description = """
        세션 종료 후 별점과 주관식 피드백을 모두 제출한 청중에게만 발표 자료 PDF를 다운로드합니다.
        """
  )
  public ResponseEntity<InputStreamResource> downloadPdf(
      @PathVariable String roomId,
      @RequestParam String audienceId
  ) {
    if (!canDownloadPdf(roomId, audienceId)) {
      throw new CustomException(GlobalErrorCode.FORBIDDEN);
    }

    String key = roomService.getPdfDownloadS3Key(roomId);
    if (key == null || key.isBlank()) {
      throw new CustomException(GlobalErrorCode.RESOURCE_NOT_FOUND);
    }

    try {
      ResponseInputStream<GetObjectResponse> s3Object = s3Client.getObject(GetObjectRequest.builder()
          .bucket(props.getS3().getBucket())
          .key(key)
          .build());

      return ResponseEntity.ok()
          .contentType(MediaType.APPLICATION_PDF)
          .contentLength(s3Object.response().contentLength())
          .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(roomId))
          .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
          .body(new InputStreamResource(s3Object));
    } catch (S3Exception e) {
      log.error("PDF 다운로드 S3 조회 실패: roomId={}, key={}", roomId, key, e);
      throw new CustomException(e.statusCode() == 404
          ? GlobalErrorCode.RESOURCE_NOT_FOUND
          : GlobalErrorCode.INTERNAL_SERVER_ERROR);
    }
  }

  private boolean canDownloadPdf(String roomId, String audienceId) {
    return roomService.isPdfDownloadEnabled(roomId)
        && roomService.getSessionStatus(roomId) == SessionStatus.ended
        && hasSubmittedWrittenFeedback(roomId, audienceId)
        && hasDownloadablePdf(roomId);
  }

  private boolean hasDownloadablePdf(String roomId) {
    String key = roomService.getPdfDownloadS3Key(roomId);
    return key != null && !key.isBlank();
  }

  private boolean hasSubmittedWrittenFeedback(String roomId, String audienceId) {
    if (audienceId == null || audienceId.isBlank()) {
      return false;
    }

    return feedbackRepository.findByRoomIdAndAudienceIdOrderByCreatedAtDesc(roomId, audienceId).stream()
        .anyMatch(this::hasRatingAndWrittenFeedback);
  }

  private boolean hasRatingAndWrittenFeedback(FeedbackEntity feedback) {
    return feedback.getRating() >= 1
        && feedback.getRating() <= 5
        && feedback.getComment() != null
        && !feedback.getComment().isBlank();
  }

  private String contentDisposition(String roomId) {
    String fileName = normalizeDownloadFileName(roomService.getPdfDownloadFileName(roomId));
    String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
    return "attachment; filename=\"boini-slides.pdf\"; filename*=UTF-8''" + encodedFileName;
  }

  private String normalizeDownloadFileName(String fileName) {
    if (fileName == null || fileName.isBlank()) {
      return "boini-slides.pdf";
    }

    String normalized = fileName.replaceAll("[\\p{Cntrl}\\\\/:*?\"<>|]+", "_").trim();
    if (normalized.isBlank()) {
      return "boini-slides.pdf";
    }

    return normalized.toLowerCase().endsWith(".pdf") ? normalized : normalized + ".pdf";
  }
}
