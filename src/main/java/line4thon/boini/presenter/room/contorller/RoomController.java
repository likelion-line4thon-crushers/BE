package line4thon.boini.presenter.room.contorller;

import jakarta.validation.Valid;
import line4thon.boini.presenter.room.dto.response.TokenResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import line4thon.boini.presenter.room.dto.request.CreateRoomRequest;
import line4thon.boini.presenter.room.dto.request.RefreshPresenterTokenRequest;
import line4thon.boini.presenter.room.service.PresenterAuthService;
import line4thon.boini.presenter.room.service.RoomService;

@Slf4j
@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

  private final RoomService roomService;
  private final PresenterAuthService presenterAuth;

  // 발표자: 방 생성
  @PostMapping
  public ResponseEntity<?> create(@Valid @RequestBody CreateRoomRequest request) {
    var response = roomService.createRoom(request);
    return ResponseEntity.ok(response);
  }

  // 발표자: 토큰 재발급
  @PostMapping("/{roomId}/presenter-token:refresh")
  public TokenResponse refreshToken(@PathVariable String roomId,
      @RequestBody RefreshPresenterTokenRequest request) {
    String newToken = presenterAuth.refreshPresenterToken(roomId, request.getPresenterKey());
    return new TokenResponse(newToken);
  }
}