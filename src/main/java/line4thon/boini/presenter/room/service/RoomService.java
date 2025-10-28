package line4thon.boini.presenter.room.service;

import java.util.UUID;
import line4thon.boini.global.common.exception.CustomException;
import line4thon.boini.global.config.AppProperties;
import line4thon.boini.presenter.room.dto.request.CreateRoomRequest;
import line4thon.boini.presenter.room.dto.response.CreateRoomResponse;
import line4thon.boini.presenter.room.exception.RoomErrorCode;
import line4thon.boini.presenter.room.service.CodeService.CodeReservation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomService {

  private final CodeService codeService;                 // 코드 예약/확정/해제 담당
  private final QrService qrService;                     // QR 생성 담당
  private final PresenterAuthService presenterAuth;      //  발표자 토큰/키 발급
  private final AppProperties props;                     // URL/WS/TTL 등 설정 접근

  // 발표자가 새로운 방을 생성할 때 호출
  public CreateRoomResponse createRoom(CreateRoomRequest request) {
    validateRequest(request);

    final String joinBase = props.getUrls().getJoinBase();
    final String wsUrl = props.getUrls().getWs();

    if (joinBase == null || joinBase.isBlank())
      throw new CustomException(RoomErrorCode.INVALID_JOIN_BASE_URL);
    if (wsUrl == null || wsUrl.isBlank())
      throw new CustomException(RoomErrorCode.INVALID_WS_URL);

    String roomId = UUID.randomUUID().toString();

    // 코드 예약 (충돌 시 내부 재시도)
    final CodeReservation reserved;

    try {
      reserved = codeService.reserveUniqueCode(roomId);
    } catch (RuntimeException e) {
      log.error("코드 예약 실패: roomId={}, err={}", roomId, e.toString());
      throw new CustomException(RoomErrorCode.CODE_RESERVE_FAILED);
    }

    final String joinUrl = joinBase + reserved.code();
    String qrB64;
    String presenterToken;
    String presenterKey;
    boolean confirmed = false;

      try {
        // join URL 및 QR 생성
        try {
          qrB64 = qrService.toBase64Png(joinUrl);
        } catch (Exception qrEx) {
          log.error("QR 생성 실패: url={}, err={}", joinUrl, qrEx.toString());
          throw new CustomException(RoomErrorCode.QR_GENERATE_FAILED);
        }

        try {
          presenterToken = presenterAuth.issuePresenterToken(roomId);
        } catch (CustomException ce) {
          // PresenterAuthService가 자체 에러코드를 던지면 그대로 전파
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

        // 모든 부가 작업 성공 시 확정
        try {
          codeService.confirmMapping(reserved, roomId);
          confirmed = true;
        } catch (RuntimeException runEx) {
          log.error("코드 확정 실패: roomId={}, code={}, err={}", roomId, reserved.code(), runEx.toString());
          throw new CustomException(RoomErrorCode.CODE_CONFIRM_FAILED);
        }

        return new CreateRoomResponse(
            roomId,
            reserved.code(),
            joinUrl,
            props.getUrls().getWs(),
            request.getCount(),
            qrB64,
            presenterToken,
            presenterKey
        );

      } catch (CustomException ex) {
        // 단계별 표준화된 예외는 예약 해제 후 그대로 전파
        safeRelease(reserved, roomId, ex);
        throw ex;
      } catch (RuntimeException ex) {
        // 예상치 못한 런타임 예외도 예약 해제 후 표준화
        safeRelease(reserved, roomId, ex);
        throw new CustomException(RoomErrorCode.UNEXPECTED);
      }
    }

  // 요청 값 검증 (count 등)
  private void validateRequest(CreateRoomRequest request) {
    if (request == null) {
      throw new CustomException(RoomErrorCode.INVALID_REQUEST);
    }
    // count 제약이 있다면 여기서 함께 검증 (예: 1~1000 사이)
    Integer count = request.getCount();
    if (count == null || count < 1) {
      throw new CustomException(RoomErrorCode.INVALID_REQUEST);
    }
  }

  // 확정 실패 또는 중간 오류 시 안전 해제
  private void safeRelease(CodeReservation reserved, String roomId, Exception cause) {
    if (reserved == null) return;
    try {
      codeService.release(reserved);
      log.warn("예약 해제 완료: roomId={}, code={}", roomId, reserved.code(), cause);
    } catch (RuntimeException re) {
      log.error("예약 해제 실패: roomId={}, code={}, err={}", roomId, reserved.code(), re.toString());
    }
  }
}
