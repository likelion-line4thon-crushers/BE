package line4thon.boini.presenter.room.contorller;

import jakarta.validation.Valid;
import line4thon.boini.presenter.room.dto.response.TokenResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import line4thon.boini.global.config.AppProperties;
import line4thon.boini.presenter.room.dto.request.CreateRoomRequest;
import line4thon.boini.presenter.room.dto.request.RefreshPresenterTokenRequest;
import line4thon.boini.presenter.room.dto.response.CreateRoomResponse;
import line4thon.boini.presenter.room.model.Room;
import line4thon.boini.presenter.room.service.PresenterAuthService;
import line4thon.boini.presenter.room.service.QrService;   // QR 사용 시
import line4thon.boini.presenter.room.service.RoomService;

@Slf4j
@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

  private final RoomService roomService;
  private final PresenterAuthService presenterAuth;
  private final QrService qr;
  private final AppProperties props;

  // 발표자: 방 생성
  @PostMapping
  public CreateRoomResponse create(@Valid @RequestBody CreateRoomRequest request) {
    Room room        = roomService.createRoom(request);
    String joinUrl   = props.getUrls().getJoinBase() + room.getCode();
    String qrB64     = qr.toBase64Png(joinUrl);
    String token     = presenterAuth.issuePresenterToken(room.getId());
    String key       = presenterAuth.generateAndStorePresenterKey(room.getId()); // 재발급키(원문)

    return new CreateRoomResponse(
        room.getId(),
        room.getCode(),
        joinUrl,
        props.getUrls().getWs(),
        qrB64,
        token,
        key
    );
  }

  // 발표자: 토큰 재발급
  @PostMapping("/{roomId}/presenter-token:refresh")
  public TokenResponse refreshToken(@PathVariable String roomId,
      @RequestBody RefreshPresenterTokenRequest request) {
    String newToken = presenterAuth.refreshPresenterToken(roomId, request.getPresenterKey());
    return new TokenResponse(newToken);
  }
}