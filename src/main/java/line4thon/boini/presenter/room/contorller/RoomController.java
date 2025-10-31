package line4thon.boini.presenter.room.contorller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Room", description = "발표자/청중 방 생성 및 입장 관련 API")
public class RoomController {

  private final RoomService roomService;
  private final PresenterAuthService presenterAuth;
  private final CodeService codeService;
  private final AudienceAuthService audienceAuth;

  @Autowired
  private RedisTemplate<String, String> redisTemplate;

  // 발표자: 방 생성
  @PostMapping
  @Operation(
      summary = "발표자 방 생성",
      description = "발표자가 새로운 방을 생성합니다."
  )
  public BaseResponse<CreateRoomResponse> create(@Valid @RequestBody CreateRoomRequest request) {
    var response = roomService.createRoom(request);
    return BaseResponse.success(response);
  }

  // 발표자: 토큰 재발급
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

  // 청중: 방 입장
  @GetMapping("/join/{code}")
  @Operation(
      summary = "청중 방 입장",
      description = """
          초대 코드(`code`)를 사용해 청중이 방에 입장합니다.  
          유효한 코드만 허용되며, 응답으로는 `roomId`, `audienceId`, `audienceToken`이 발급됩니다.
          """
  )
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