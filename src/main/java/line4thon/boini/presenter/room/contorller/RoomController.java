package line4thon.boini.presenter.room.contorller;

import io.jsonwebtoken.Claims;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import line4thon.boini.audience.room.dto.request.LeaveRoomRequest;
import line4thon.boini.audience.room.dto.response.JoinResponse;
import line4thon.boini.audience.room.dto.response.LeaveRoomResponse;
import line4thon.boini.audience.room.service.AudienceAuthService;
import line4thon.boini.global.common.exception.CustomException;
import line4thon.boini.global.common.response.BaseResponse;
import line4thon.boini.global.jwt.service.JwtService;
import line4thon.boini.global.websocket.JwtHandshakeInterceptor;
import line4thon.boini.presenter.page.service.PageService;
import line4thon.boini.presenter.room.dto.response.CreateRoomResponse;
import line4thon.boini.presenter.room.dto.response.TokenResponse;
import line4thon.boini.presenter.room.service.CodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import line4thon.boini.presenter.room.dto.request.CreateRoomRequest;
import line4thon.boini.presenter.room.dto.request.RefreshPresenterTokenRequest;
import line4thon.boini.presenter.room.service.PresenterAuthService;
import line4thon.boini.global.jwt.exception.JwtErrorCode;
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
  private final PageService pageService;
  private final JwtHandshakeInterceptor jwtHandshakeInterceptor;
  private final JwtService jwtService;

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

    //Redis에 유저 슬라이드1에 추가
    String key2 = "room:" + roomId + ":slide:1";
    redisTemplate.opsForSet().add(key2, issued.audienceId());

    return BaseResponse.success(new JoinResponse(
        roomId,
        code,
        issued.audienceId(),
        issued.audienceToken()
    ));
  }

  // 청중: 방 퇴장
  @PostMapping("/leave/{roomId}")
  @Operation(
          summary = "청중 방 퇴장",
          description = """
          `roomId`, `audienceId`, `audienceToken`으로 해당 청중을 방에서 퇴장시킵니다.
          """
  )
  public BaseResponse<LeaveRoomResponse> leaveRoom(@PathVariable("roomId") String roomId, @RequestBody LeaveRoomRequest request) {
    return roomService.leaveRoom(roomId, request);
  }

  // 발표자 : 세션 종료(+ AI 리포트 + Redis 청소)
  @DeleteMapping("/close/{roomId}")
  @Operation(
          summary = "발표자 세션 종료",
          description = """
          `roomId`와 `발표자 JWT`로 세션을 종료시킵니다.
          """
  )
  public ResponseEntity closeRoom(@PathVariable("roomId") String roomId, @RequestHeader(value = "Authorization", required = false) String authHeader) {

//    String token = jwtHandshakeInterceptor.extractBearer(request.getHeader("Authorization"));
//
//    if (token == null)
//      throw new CustomException(JwtErrorCode.JWT_INVALID);

    String token = null;
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
      token = authHeader.substring(7); // "Bearer " 잘라내기
    }

    System.out.println("token: " + token);

    roomService.closeRoom(roomId, token);

    return ResponseEntity.ok("방을 성공적으로 삭제하였습니다.");
  }


}