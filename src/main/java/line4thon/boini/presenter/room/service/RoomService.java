package line4thon.boini.presenter.room.service;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import io.jsonwebtoken.Claims;
import line4thon.boini.audience.room.dto.request.LeaveRoomRequest;
import line4thon.boini.audience.room.dto.response.JoinResponse;
import line4thon.boini.audience.room.dto.response.LeaveRoomResponse;
import line4thon.boini.audience.room.service.AudienceAuthService;
import line4thon.boini.global.common.exception.CustomException;
import line4thon.boini.global.common.exception.GlobalErrorCode;
import line4thon.boini.global.common.response.BaseResponse;
import line4thon.boini.global.config.AppProperties;
import line4thon.boini.global.jwt.service.JwtService;
import line4thon.boini.presenter.page.service.PageService;
import line4thon.boini.presenter.room.dto.request.CreateRoomRequest;
import line4thon.boini.presenter.room.dto.response.CreateRoomResponse;
import line4thon.boini.presenter.room.exception.RoomErrorCode;
import line4thon.boini.presenter.room.service.CodeService.CodeReservation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomService {

  private final CodeService codeService;                 // 코드 예약/확정/해제 담당
  private final QrService qrService;                     // QR 생성 담당
  private final PresenterAuthService presenterAuth;      //  발표자 토큰/키 발급
  private final AppProperties props;                     // URL/WS/TTL 등 설정 접근
  private final PageService pageService;
  private final AudienceAuthService audienceAuthService;
  private final S3Client s3Client;
  private final JwtService jwtService;


  @Autowired
  private RedisTemplate<String, String> redisTemplate;  //Redis
  private RedisTemplate<String, Object> objectRedisTemplate;  //Redis

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
    String deckId = UUID.randomUUID().toString(); // 슬라이드 묶음 식별자

    // 코드 예약 (충돌 시 내부 재시도)
    final CodeReservation reserved;

    try {
      reserved = codeService.reserveUniqueCode(roomId);
    } catch (RuntimeException e) {
      log.error("코드 예약 실패: roomId={}, err={}", roomId, e.toString());
      throw new CustomException(RoomErrorCode.CODE_RESERVE_FAILED);
    }

    redisTemplate.opsForSet().size("room:" + roomId + ":audience:online");  //방 redis KEY 생성

    redisTemplate.opsForValue().set("room:" + roomId + ":presenterPage", "1"); //방 redis KEY 생성

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

        //Redis에 페이지 별로 set 추가
        int totalPages = request.getTotalPages(); // 이미 int라면 int totalPages = ...
        for (int i = 1; i <= totalPages; i++) {
          // i는 0부터 totalPages-1까지
          String key = "room:" + roomId + ":slide:" + i;
          redisTemplate.opsForSet().add(key, "_init_");
        }

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
        // 단계별 표준화된 예외는 예약 해제 후 그대로 전파
        safeRelease(reserved, roomId, ex, confirmed);
        throw ex;
      } catch (RuntimeException ex) {
        // 예상치 못한 런타임 예외도 예약 해제 후 표준화
        safeRelease(reserved, roomId, ex, confirmed);
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

    Integer totalPages = request.getTotalPages();
    if (totalPages == null || totalPages < 1 /* || totalPages > 5000 */) {
      throw new CustomException(RoomErrorCode.INVALID_REQUEST);
    }
  }

  // 확정 실패 또는 중간 오류 시 안전 해제
  private void safeRelease(CodeReservation reserved, String roomId, Exception cause, boolean confirmed) {
    if (reserved == null) return;

    if (confirmed) {
      log.error("확정 이후 예외 발생(수동 점검 필요): roomId={}, code={}, cause={}",
          roomId, reserved.code(), cause.toString());
      // 필요 시 여기서 롤백 로직이 있다면 호출 (예: codeService.rollbackConfirmed(reserved))
      return;
    }

    try {
      codeService.release(reserved);
      log.warn("예약 해제 완료: roomId={}, code={}, cause={}", roomId, reserved.code(), cause.toString());
    } catch (RuntimeException re) {
      log.error("예약 해제 실패: roomId={}, code={}, err={}", roomId, reserved.code(), re.toString());
    }
  }

  //방 퇴장
  public BaseResponse<LeaveRoomResponse> leaveRoom(String roomId, LeaveRoomRequest request){
    //slide 개수
    int slides = pageService.countSlideKeys(roomId);


    //online Redis에서 해당 청중 삭제
    for(int i = 1; i <= slides; i++) {
      String key2 = "room:" + roomId + ":slide:1";

      if(redisTemplate.opsForSet().isMember(key2, request.getAudienceId())) {
        redisTemplate.opsForSet().remove(key2, request.getAudienceId());
      }
    }

    //online Redis에서 해당 청중 삭제
    String key = "room:" + roomId + ":audience:online";
    redisTemplate.opsForSet().remove(key, request.getAudienceId());

    return BaseResponse.success(new LeaveRoomResponse(
            roomId,
            request.getAudienceId(),
            request.getAudienceJWT()
    ));
  }

  //방에 대한 모든 정보 삭제
  public void closeRoom(String roomId, String token) {

//    String key = "room:" + roomId + ":presenterKeyHash";
//    String presenterToken = redisTemplate.opsForValue().get(key);
//
//    System.out.println("presenterToken: " + presenterToken);
//
//    if(!presenterToken.equals(token)) {
//      throw new CustomException(RoomErrorCode.PRESENTER_KEY_NOT_MATCH);
//    }
    Claims claims;
    try {
      claims = jwtService.parse(token);
    } catch (io.jsonwebtoken.ExpiredJwtException e) {
      log.error("JWT expired: {}", e.getMessage());
      throw new CustomException(RoomErrorCode.JWT_EXPIRED);
    } catch (io.jsonwebtoken.SignatureException e) {
      log.error("JWT signature invalid: {}", e.getMessage());
      throw new CustomException(RoomErrorCode.JWT_INVALID_SIGNATURE);
    } catch (io.jsonwebtoken.MalformedJwtException e) {
      log.error("JWT malformed: {}", e.getMessage());
      throw new CustomException(RoomErrorCode.JWT_INVALID);
    } catch (Exception e) {
      log.error("JWT parse failed: {}", e.getMessage());
      throw new CustomException(RoomErrorCode.PRESENTER_KEY_NOT_MATCH);
    }

    // 2️⃣ 클레임에서 roomId와 role 꺼내기
    String tokenRoomId = claims.get("roomId", String.class);
    String role = claims.get("role", String.class);

    // 3️⃣ 유효성 검증
    if (!roomId.equals(tokenRoomId)) {
      throw new CustomException(RoomErrorCode.ROOM_ID_NOT_MATCH);
    }

    if (!"presenter".equalsIgnoreCase(role)) {
      throw new CustomException(RoomErrorCode.PRESENTER_KEY_NOT_MATCH);
    }


    try{
      //key 패턴
      String pattern = "room:" + roomId + ":*";
      String key2 = "room:" + roomId + ":code";
      String code = redisTemplate.opsForValue().get(key2);
      if (code == null) {
        throw new CustomException(RoomErrorCode.CODE_INVALID);
      }
      String key3 = "code:" + code;

      // Redis에서 해당 패턴에 매칭되는 모든 키 가져오기
      Set<String> keys = redisTemplate.keys(pattern);
      if (keys != null && !keys.isEmpty()) {
        redisTemplate.delete(keys);
        redisTemplate.delete(key3);
//        objectRedisTemplate.delete(keys);
        log.info("방 관련 키 전부 제거 완료 : roomId={}", roomId);
      } else {
        throw new CustomException(GlobalErrorCode.RESOURCE_NOT_FOUND);
      }
    } catch (Exception e) {
      throw new CustomException(GlobalErrorCode.INTERNAL_SERVER_ERROR);
    }



    try {
      String bucket = props.getS3().getBucket();

      String rootPrefix = props.getS3().getRootPrefix(); // presentations

      String prefix = rootPrefix + "/" + roomId + "/";

      log.info("S3 삭제 시작: bucket={}, prefix={}", bucket, prefix);

      ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
              .bucket(bucket)
              .prefix(prefix)
              .build();


      ListObjectsV2Response listResponse = s3Client.listObjectsV2(listRequest);

      List<S3Object> contents = listResponse.contents();


      if (contents == null || contents.isEmpty()) {
//        throw new CustomException(GlobalErrorCode.RESOURCE_NOT_FOUND);

      }
      else{
        // 삭제할 객체 목록 생성
        List<ObjectIdentifier> objectsToDelete = listResponse.contents().stream()
                .map(S3Object::key)
                .map(k -> ObjectIdentifier.builder().key(k).build())
                .collect(Collectors.toList());

        // 일괄 삭제
        DeleteObjectsRequest deleteRequest = DeleteObjectsRequest.builder()
                .bucket(bucket)
                .delete(Delete.builder().objects(objectsToDelete).build())
                .build();

        s3Client.deleteObjects(deleteRequest);
      }



    } catch (S3Exception e) {
      throw new CustomException(GlobalErrorCode.S3_DELETE_FAILED);
    } catch (Exception e) {
      log.error("S3 삭제 중 알 수 없는 오류 발생", e);
      throw new CustomException(GlobalErrorCode.INTERNAL_SERVER_ERROR);
    }
  }
}
