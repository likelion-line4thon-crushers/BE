package line4thon.boini.presenter.room.contorller;

import jakarta.validation.Valid;
import line4thon.boini.audience.room.dto.response.JoinResponse;
import line4thon.boini.audience.room.service.AudienceAuthService;
import line4thon.boini.global.common.response.BaseResponse;
import line4thon.boini.presenter.room.dto.response.CreateRoomResponse;
import line4thon.boini.presenter.room.dto.response.TokenResponse;
import line4thon.boini.presenter.room.service.CodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import line4thon.boini.presenter.room.dto.request.CreateRoomRequest;
import line4thon.boini.presenter.room.dto.request.RefreshPresenterTokenRequest;
import line4thon.boini.presenter.room.service.PresenterAuthService;
import line4thon.boini.presenter.room.service.RoomService;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

  private final RoomService roomService;
  private final PresenterAuthService presenterAuth;
  private final CodeService codeService;
  private final AudienceAuthService audienceAuth;

  @Autowired
  private RedisTemplate<String, String> redisTemplate;

  // 발표자: 방 생성
  @PostMapping
  public BaseResponse<CreateRoomResponse> create(@Valid @RequestBody CreateRoomRequest request) {
    var response = roomService.createRoom(request);
    return BaseResponse.success(response);
  }

  // 발표자: 토큰 재발급
  @PostMapping("/{roomId}/presenter-token:refresh")
  public BaseResponse<TokenResponse> refreshToken(@PathVariable String roomId,
      @RequestBody RefreshPresenterTokenRequest request) {
    String newToken = presenterAuth.refreshPresenterToken(roomId, request.getPresenterKey());
    return BaseResponse.success(new TokenResponse(newToken));
  }

  // 청중: 방 입장
  @GetMapping("/join/{code}")
  public BaseResponse<JoinResponse> joinByPath(@PathVariable("code") String code) {
    // code -> roomId 확인 (CONFIRMED만 허용)
    String roomId = codeService.resolveRoomIdByCodeOrThrow(code);
    var issued = audienceAuth.issueAudienceToken(roomId);

    //Redis에 유저ID 추가
    String key = "room:" + roomId + ":audience:online";
    redisTemplate.opsForSet().add(key, issued.audienceId());

    return BaseResponse.success(new JoinResponse(
        roomId,
        code,
        issued.audienceId(),
        issued.audienceToken()
    ));
  }
}