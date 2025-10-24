package line4thon.boini.presenter.room.service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import line4thon.boini.global.common.exception.CustomException;
import line4thon.boini.global.common.exception.GlobalErrorCode;
import line4thon.boini.global.jwt.service.JwtService;
import line4thon.boini.presenter.room.dto.request.CreateRoomRequest;
import line4thon.boini.presenter.room.model.Room;
import line4thon.boini.presenter.room.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RoomService {

  // 방 데이터의 기본 보존 시간 (24시간)
  private static final long TTL_SECONDS = 24 * 60 * 60;

  private final RoomRepository repository;
  private final StringRedisTemplate srt;
  private final JwtService jwt;

  // 발표자가 새로운 방을 생성할 때 호출
  public Room createRoom(CreateRoomRequest request) {
    String roomId = UUID.randomUUID().toString();
    String code = reserveUniqueCode(roomId);

    Room room = Room.builder()
        .id(roomId)
        .code(code)
        .options(request.getOptions())
        .createdAt(Instant.now())
        .build();

    repository.save(room);
    repository.setCodeMapping(code, roomId, TTL_SECONDS);
    return room;
  }

  // 중복되지 않는 6자리 초대코드를 생성하고 Redis에 등록
  private String reserveUniqueCode(String roomId) {
    for (int i = 0; i < 12; i++) {  // 최대 12번 시도
      String c = RandomStringUtils.random(6, "ABCDEFGHJKLMNPQRSTUVWXYZ23456789");
      // 해당 코드가 비어 있으면 등록 (code → roomId)
      if (Boolean.TRUE.equals(srt.opsForValue().setIfAbsent("room:code:" + c, roomId,
          Duration.ofSeconds(TTL_SECONDS))))
        return c;   // 성공 시 코드 반환
    }
    // 모든 시도가 실패하면 예외 발생
    throw new CustomException(GlobalErrorCode.INTERNAL_SERVER_ERROR);
  }

  // 발표 종료 시 TTL을 10분으로 줄여 Redis 데이터를 조기 만료시킴
  public void endRoom(String roomId) {
    Duration grace = Duration.ofMinutes(10);

    srt.getRequiredConnectionFactory().getConnection()
        .keyCommands().expire(("room:" + roomId).getBytes(), grace);
  }
}
