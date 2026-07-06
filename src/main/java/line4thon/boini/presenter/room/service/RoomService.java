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

  private String pdfDownloadPolicyKey(String roomId) {
    return "room:" + roomId + ":pdfDownload:enabled";
  }

  public String pdfDownloadS3Key(String roomId) {
    return "room:" + roomId + ":pdfDownload:s3Key";
  }

  public String pdfDownloadFileNameKey(String roomId) {
    return "room:" + roomId + ":pdfDownload:fileName";
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

  public boolean setPdfDownloadPolicy(String roomId, boolean enabled) {
    redisTemplate.opsForValue().set(pdfDownloadPolicyKey(roomId), String.valueOf(enabled));
    return enabled;
  }

  public boolean isPdfDownloadEnabled(String roomId) {
    return Boolean.parseBoolean(redisTemplate.opsForValue().get(pdfDownloadPolicyKey(roomId)));
  }

  public String getPdfDownloadS3Key(String roomId) {
    return redisTemplate.opsForValue().get(pdfDownloadS3Key(roomId));
  }

  public String getPdfDownloadFileName(String roomId) {
    return redisTemplate.opsForValue().get(pdfDownloadFileNameKey(roomId));
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
    redisTemplate.opsForValue().set("room:"+roomId+":option:feedback", "true");
    redisTemplate.opsForValue().set("room:"+roomId+":option:slideUnlock", "true");
    redisTemplate.opsForValue().set(pdfDownloadPolicyKey(roomId), "false");
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
    // 명시적 "나가기": 해당 청중의 모든 연결/집계를 강제로 정리한다.
    forceRemoveAudience(roomId, request.getAudienceId());

    return BaseResponse.success(new LeaveRoomResponse(
            roomId,
            request.getAudienceId(),
            request.getAudienceJWT()
    ));
  }

  // ---- 청중 온라인 상태(presence) 관리 ---------------------------------------
  // presence 는 WebSocket 연결 생명주기에 종속된다.
  //  - 온라인 집합(:audience:online) = 현재 접속 중인 audienceId 집합 (청중 수의 원천)
  //  - 세션 집합(:audience:sessions:{audienceId}) = 해당 청중의 활성 WS 세션 id 집합
  // 새로고침 시 이전 소켓의 DISCONNECT 와 새 소켓의 CONNECT 가 경쟁하더라도,
  // "마지막 세션이 사라질 때만" 온라인 집합에서 제거하므로 청중 수가 잘못 줄지 않는다.

  private String onlineKey(String roomId) {
    return "room:" + roomId + ":audience:online";
  }

  private String sessionsKey(String roomId, String audienceId) {
    return "room:" + roomId + ":audience:sessions:" + audienceId;
  }

  // wsSessionId -> "roomId|audienceId" 매핑. DISCONNECT 이벤트에서 세션 속성에 의존하지 않고
  // wsSessionId 만으로 청중 신원을 복원하기 위한 역방향 인덱스.
  private String connKey(String wsSessionId) {
    return "ws:conn:" + wsSessionId;
  }

  /**
   * WebSocket 연결(CONNECT) 시 호출. 청중의 WS 세션을 등록한다.
   * 세션이 처음 등록되어 청중이 새로 온라인이 된 경우에만 청중 수를 브로드캐스트한다.
   */
  public void registerAudiencePresence(String roomId, String audienceId, String wsSessionId) {
    if (roomId == null || audienceId == null || wsSessionId == null) {
      return;
    }

    java.time.Duration ttl = java.time.Duration.ofSeconds(props.getRoom().getTtlSeconds());

    // DISCONNECT 에서 사용할 역방향 매핑 저장 (CONNECT 시점엔 신원이 확실함)
    redisTemplate.opsForValue().set(connKey(wsSessionId), roomId + "|" + audienceId, ttl);

    String sessionsKey = sessionsKey(roomId, audienceId);
    Long addedSession = redisTemplate.opsForSet().add(sessionsKey, wsSessionId);
    redisTemplate.expire(sessionsKey, ttl);

    if (addedSession == null || addedSession == 0) {
      return; // 같은 세션의 중복 CONNECT → 아무 것도 하지 않음
    }

    Long addedOnline = redisTemplate.opsForSet().add(onlineKey(roomId), audienceId);
    redisTemplate.expire(onlineKey(roomId), ttl); // 방 종료 누락 대비 안전망 TTL
    if (addedOnline != null && addedOnline >= 1) {
      broadcastAudienceCount(roomId);
    }
  }

  /**
   * WebSocket 연결 종료(DISCONNECT) 시 호출. wsSessionId 로 청중 신원을 복원해 세션을 해제한다.
   * 청중의 마지막 세션이 사라진 경우에만 온라인 집합/슬라이드 집계에서 제거하고 브로드캐스트한다.
   */
  public void unregisterAudienceBySession(String wsSessionId) {
    if (wsSessionId == null) {
      return;
    }

    String mapping = redisTemplate.opsForValue().get(connKey(wsSessionId));
    redisTemplate.delete(connKey(wsSessionId));
    if (mapping == null) {
      log.debug("[presence] DISCONNECT 매핑 없음(청중 아님/이미 처리): sessionId={}", wsSessionId);
      return; // 청중 세션이 아니거나 이미 처리됨
    }

    int sep = mapping.indexOf('|');
    if (sep < 0) {
      return;
    }
    String roomId = mapping.substring(0, sep);
    String audienceId = mapping.substring(sep + 1);

    redisTemplate.opsForSet().remove(sessionsKey(roomId, audienceId), wsSessionId);

    Long remaining = redisTemplate.opsForSet().size(sessionsKey(roomId, audienceId));
    if (remaining != null && remaining > 0) {
      log.debug("[presence] DISCONNECT 세션 해제(잔여 세션 있음): roomId={}, audienceId={}, 잔여={}",
          roomId, audienceId, remaining);
      return; // 다른 탭/세션이 아직 살아있음(새로고침 포함) → 온라인 유지
    }

    Long removedOnline = redisTemplate.opsForSet().remove(onlineKey(roomId), audienceId);
    removeFromSlideSets(roomId, audienceId);
    int count = currentOnlineCount(roomId);
    log.debug("[presence] DISCONNECT 청중 오프라인: roomId={}, audienceId={}, removedOnline={}, 남은청중={}",
        roomId, audienceId, removedOnline, count);
    if (removedOnline != null && removedOnline >= 1) {
      broadcastAudienceCount(roomId);
    }
  }

  /** 명시적 퇴장: 청중의 모든 세션/집계를 제거하고 청중 수를 브로드캐스트한다. */
  public void forceRemoveAudience(String roomId, String audienceId) {
    if (roomId == null || audienceId == null) {
      return;
    }

    // 이 청중의 모든 WS 세션에 대한 역방향 매핑까지 정리
    java.util.Set<String> sessionIds = redisTemplate.opsForSet().members(sessionsKey(roomId, audienceId));
    if (sessionIds != null) {
      for (String sid : sessionIds) {
        redisTemplate.delete(connKey(sid));
      }
    }

    redisTemplate.delete(sessionsKey(roomId, audienceId));
    redisTemplate.opsForSet().remove(onlineKey(roomId), audienceId);
    removeFromSlideSets(roomId, audienceId);
    broadcastAudienceCount(roomId);
  }

  private void removeFromSlideSets(String roomId, String audienceId) {
    String totalpage = redisTemplate.opsForValue().get("room:" + roomId + ":totalPage");
    if (totalpage == null) {
      return;
    }
    try {
      int slides = Integer.parseInt(totalpage);
      for (int i = 1; i <= slides; i++) {
        redisTemplate.opsForSet().remove("room:" + roomId + ":slide:" + i, audienceId);
      }
    } catch (NumberFormatException ignored) {
      // totalPage 값이 손상된 경우 슬라이드 정리는 건너뛴다
    }
  }

  private void broadcastAudienceCount(String roomId) {
    broker.convertAndSend("/topic/presentation/" + roomId + "/audienceCount", currentOnlineCount(roomId));
  }

  public int currentOnlineCount(String roomId) {
    Long size = redisTemplate.opsForSet().size(onlineKey(roomId));
    return size == null ? 0 : size.intValue();
  }

  public void closeRoom(String roomId) {

    try {
      setSessionStatus(roomId, SessionStatus.ended);
    } catch (Exception e) {
      log.warn("ended 브로드캐스트 실패(삭제는 계속 진행): roomId={}, err={}", roomId, e.toString());
    }

    try {
      cleanupPresenceKeys(roomId);
    } catch (Exception e) {
      log.warn("presence 키 정리 실패: roomId={}, err={}", roomId, e.toString());
    }
  }

  /** 방 종료 시 presence 관련 Redis 키(online, sessions:*, ws:conn:*, slide:*)를 정리한다. */
  private void cleanupPresenceKeys(String roomId) {
    java.util.Set<String> audienceIds = redisTemplate.opsForSet().members(onlineKey(roomId));
    if (audienceIds != null) {
      for (String audienceId : audienceIds) {
        java.util.Set<String> sessionIds = redisTemplate.opsForSet().members(sessionsKey(roomId, audienceId));
        if (sessionIds != null) {
          for (String sid : sessionIds) {
            redisTemplate.delete(connKey(sid));
          }
        }
        redisTemplate.delete(sessionsKey(roomId, audienceId));
      }
    }

    redisTemplate.delete(onlineKey(roomId));

    String totalpage = redisTemplate.opsForValue().get("room:" + roomId + ":totalPage");
    if (totalpage != null) {
      try {
        int slides = Integer.parseInt(totalpage);
        for (int i = 1; i <= slides; i++) {
          redisTemplate.delete("room:" + roomId + ":slide:" + i);
        }
      } catch (NumberFormatException ignored) {
        // totalPage 손상 시 슬라이드 정리는 건너뛴다
      }
    }
  }
}
