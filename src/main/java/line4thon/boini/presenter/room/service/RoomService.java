package line4thon.boini.presenter.room.service;

import java.util.UUID;
import line4thon.boini.global.config.AppProperties;
import line4thon.boini.presenter.room.dto.request.CreateRoomRequest;
import line4thon.boini.presenter.room.dto.response.CreateRoomResponse;
import line4thon.boini.presenter.room.service.CodeService.CodeReservation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomService {

  private final CodeService codeService;                 // [ADDED] 코드 예약/확정/해제 담당
  private final QrService qrService;                     // [ADDED] QR 생성 담당
  private final PresenterAuthService presenterAuth;      // [ADDED] 발표자 토큰/키 발급
  private final AppProperties props;                     // [ADDED] URL/WS/TTL 등 설정 접근

  // 발표자가 새로운 방을 생성할 때 호출
  public CreateRoomResponse createRoom(CreateRoomRequest request) {
    String roomId = UUID.randomUUID().toString();

    // 코드 예약 (충돌 시 내부 재시도)
    CodeReservation reserved = codeService.reserveUniqueCode(roomId);

    try {
      // join URL 및 QR 생성
      String joinUrl = props.getUrls().getJoinBase() + reserved.code(); // ex) https://.../j/JTJ7V3
      String qrB64   = qrService.toBase64Png(joinUrl);

      // 발표자 토큰/재발급 키 발급
      String presenterToken = presenterAuth.issuePresenterToken(roomId);
      String presenterKey   = presenterAuth.generateAndStorePresenterKey(roomId);

      // 모든 부가 작업 성공 시 확정
      codeService.confirmMapping(reserved, roomId);

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
    } catch (RuntimeException e) {
      // 중간 실패 시 예약 해제
      log.warn("방 생성 중 오류 발생 — 예약된 코드를 해제합니다. roomId={}, code={}", roomId, reserved.code(), e);
      codeService.release(reserved);
      throw e;
    }
  }
}
