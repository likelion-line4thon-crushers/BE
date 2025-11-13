package line4thon.boini.presenter.room.service;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.Map;

import io.jsonwebtoken.Claims;
import line4thon.boini.audience.liveFeedback.service.LiveFeedbackService;
import line4thon.boini.audience.room.dto.request.LeaveRoomRequest;
import line4thon.boini.audience.room.dto.response.JoinResponse;
import line4thon.boini.audience.room.dto.response.LeaveRoomResponse;
import line4thon.boini.audience.room.service.AudienceAuthService;
import line4thon.boini.global.common.exception.CustomException;
import line4thon.boini.global.common.exception.GlobalErrorCode;
import line4thon.boini.global.common.response.BaseResponse;
import line4thon.boini.global.config.AppProperties;
import line4thon.boini.global.jwt.service.JwtService;
import line4thon.boini.presenter.aiReport.exception.ReportErrorCode;
import line4thon.boini.presenter.page.service.PageService;
import line4thon.boini.presenter.room.dto.request.CreateRoomRequest;
import line4thon.boini.presenter.room.dto.response.CreateRoomResponse;
import line4thon.boini.presenter.room.entity.Report;
import line4thon.boini.presenter.room.exception.RoomErrorCode;
import line4thon.boini.presenter.room.repository.ReportRepository;
import line4thon.boini.presenter.room.service.CodeService.CodeReservation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import line4thon.boini.presenter.room.entity.SessionStatus;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomService {

  private final CodeService codeService;
  private final QrService qrService;
  private final PresenterAuthService presenterAuth;
  private final AppProperties props;
  private final PageService pageService;
  private final AudienceAuthService audienceAuthService;
  private final S3Client s3Client;
  private final JwtService jwtService;
  private final LiveFeedbackService liveFeedbackService;


  @Autowired
  private RedisTemplate<String, String> redisTemplate;
  private final ReportRepository reportRepository;

  private final SimpMessagingTemplate broker;

  private String sessionStatusKey(String roomId) {
    return "room:" + roomId + ":session:status";
  }

  public void initSessionStatus(String roomId) {
    String key = sessionStatusKey(roomId);
    if (redisTemplate.opsForValue().get(key) == null) {
      redisTemplate.opsForValue().set(key, SessionStatus.waiting.name());
    }
  }

  public SessionStatus getSessionStatus(String roomId) {
    String v = redisTemplate.opsForValue().get(sessionStatusKey(roomId));
    return (v == null) ? SessionStatus.waiting : SessionStatus.valueOf(v);
  }

  public void setSessionStatus(String roomId, SessionStatus status) {
    redisTemplate.opsForValue().set(sessionStatusKey(roomId), status.name());

    Map<String, String> evt = Map.of(
        "type", "SESSION_STATE",
        "status", status.name(),
        "updatedAt", String.valueOf(System.currentTimeMillis())
    );
    broker.convertAndSend("/topic/p/" + roomId + "/public", evt);
  }

  public CreateRoomResponse createRoom(CreateRoomRequest request, String wsUrl) {
    validateRequest(request);

    final String joinBase = props.getUrls().getJoinBase();

    if (joinBase == null || joinBase.isBlank())
      throw new CustomException(RoomErrorCode.INVALID_JOIN_BASE_URL);
    if (wsUrl == null || wsUrl.isBlank())
      throw new CustomException(RoomErrorCode.INVALID_WS_URL);

    String roomId = UUID.randomUUID().toString();
    String deckId = UUID.randomUUID().toString();

    final CodeReservation reserved;

    try {
      reserved = codeService.reserveUniqueCode(roomId);
    } catch (RuntimeException e) {
      log.error("코드 예약 실패: roomId={}, err={}", roomId, e.toString());
      throw new CustomException(RoomErrorCode.CODE_RESERVE_FAILED);
    }

    redisTemplate.opsForSet().size("room:" + roomId + ":audience:online");

    redisTemplate.opsForValue().set("room:" + roomId + ":presenterPage", "1");

    redisTemplate.opsForValue().set("room:" + roomId + ":totalPage", String.valueOf(request.getTotalPages())); //방 redis KEY 생성

    redisTemplate.opsForValue().set("room:"+roomId+":option:sticker", "true");
    redisTemplate.opsForValue().set("room:"+roomId+":option:question", "true");
    redisTemplate.opsForValue().set("room:"+roomId+":option:feedback", "false");
    redisTemplate.opsForValue().set("room:"+roomId+":option:slideUnlock", "true");
    redisTemplate.opsForValue().set("room:"+roomId+":maxSlide", "1");

    liveFeedbackService.initFeedbackHashes(roomId, request.getTotalPages());

    final String joinUrl = joinBase + reserved.code();
    String qrB64;
    String presenterToken;
    String presenterKey;
    boolean confirmed = false;

      try {
        try {
          qrB64 = qrService.toBase64Png(joinUrl);
        } catch (Exception qrEx) {
          log.error("QR 생성 실패: url={}, err={}", joinUrl, qrEx.toString());
          throw new CustomException(RoomErrorCode.QR_GENERATE_FAILED);
        }

        try {
          presenterToken = presenterAuth.issuePresenterToken(roomId);
        } catch (CustomException ce) {
          throw ce;
        } catch (Exception tokenEx) {
          log.error("발표자 토큰 발급 실패: roomId={}, err={}", roomId, tokenEx.toString());
          throw new CustomException(RoomErrorCode.PRESENTER_TOKEN_ISSUE_FAILED);
        }

        try {
          presenterKey = presenterAuth.generateAndStorePresenterKey(roomId);
        } catch (CustomException ex) {
          throw ex;
        } catch (Exception keyEx) {
          log.error("발표자 키 발급 실패: roomId={}, err={}", roomId, keyEx.toString());
          throw new CustomException(RoomErrorCode.PRESENTER_KEY_ISSUE_FAILED);
        }

        try {
          codeService.confirmMapping(reserved, roomId);
          confirmed = true;
        } catch (RuntimeException runEx) {
          log.error("코드 확정 실패: roomId={}, code={}, err={}", roomId, reserved.code(), runEx.toString());
          throw new CustomException(RoomErrorCode.CODE_CONFIRM_FAILED);
        }

        int totalPages = request.getTotalPages();
        for (int i = 1; i <= totalPages; i++) {
          String key1 = "room:" + roomId + ":slide:" + i;
          String key2 = "room:" + roomId + ":revisit:" + i;
          redisTemplate.opsForSet().add(key1, "_init_");
          redisTemplate.opsForValue().set(key2, "0");
        }

        String key2= "room:" + roomId + ":deckId";
        redisTemplate.opsForValue().set(key2, deckId);

        Report report = Report.builder()
            .roomId(roomId)
            .emojiCount(null)
            .questionCount(null)
            .attentionSlide(null)
            .top3Question(null)
            .popularEmoji(null)
            .popularQuestion(null)
            .revisit(null)
            .build();

        reportRepository.save(report);

        initSessionStatus(roomId);

        return new CreateRoomResponse(
            roomId,
            reserved.code(),
            joinUrl,
            wsUrl,
            request.getCount(),
            request.getTotalPages(),
            qrB64,
            presenterToken,
            presenterKey,
            deckId
        );

      } catch (CustomException ex) {
        safeRelease(reserved, roomId, ex, confirmed);
        throw ex;
      } catch (RuntimeException ex) {
        safeRelease(reserved, roomId, ex, confirmed);
        throw new CustomException(RoomErrorCode.UNEXPECTED);
      }
    }

  private void validateRequest(CreateRoomRequest request) {
    if (request == null) {
      throw new CustomException(RoomErrorCode.INVALID_REQUEST);
    }
    Integer count = request.getCount();
    if (count == null || count < 1) {
      throw new CustomException(RoomErrorCode.INVALID_REQUEST);
    }

    Integer totalPages = request.getTotalPages();
    if (totalPages == null || totalPages < 1 ) {
      throw new CustomException(RoomErrorCode.INVALID_REQUEST);
    }
  }

  private void safeRelease(CodeReservation reserved, String roomId, Exception cause, boolean confirmed) {
    if (reserved == null) return;

    if (confirmed) {
      log.error("확정 이후 예외 발생(수동 점검 필요): roomId={}, code={}, cause={}",
          roomId, reserved.code(), cause.toString());
      return;
    }

    try {
      codeService.release(reserved);
      log.warn("예약 해제 완료: roomId={}, code={}, cause={}", roomId, reserved.code(), cause.toString());
    } catch (RuntimeException re) {
      log.error("예약 해제 실패: roomId={}, code={}, err={}", roomId, reserved.code(), re.toString());
    }
  }

  public BaseResponse<LeaveRoomResponse> leaveRoom(String roomId, LeaveRoomRequest request){
    String totalpage = redisTemplate.opsForValue().get("room:" + roomId + ":totalPage");
    if (totalpage == null) {
      throw new CustomException(ReportErrorCode.TOTAL_PAGE_NULL);
    }
    int slides = Integer.parseInt(totalpage);


    for(int i = 1; i <= slides; i++) {
      String key2 = "room:" + roomId + ":slide:1";

      if(redisTemplate.opsForSet().isMember(key2, request.getAudienceId())) {
        redisTemplate.opsForSet().remove(key2, request.getAudienceId());
      }
    }

    String key = "room:" + roomId + ":audience:online";
    redisTemplate.opsForSet().remove(key, request.getAudienceId());

    return BaseResponse.success(new LeaveRoomResponse(
            roomId,
            request.getAudienceId(),
            request.getAudienceJWT()
    ));
  }

  public void closeRoom(String roomId) {

    try {
      setSessionStatus(roomId, SessionStatus.ended);
    } catch (Exception e) {
      log.warn("ended 브로드캐스트 실패(삭제는 계속 진행): roomId={}, err={}", roomId, e.toString());
    }

  }
}
