package line4thon.boini.presenter.room.repository;

import lombok.RequiredArgsConstructor;
import line4thon.boini.presenter.room.model.Room;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import java.time.Duration;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RedisRoomRepository implements RoomRepository {

  private final RedisTemplate<String, Object> redis;
  private final StringRedisTemplate srt;

  // room:{roomId} 형식의 Redis 키 생성
  private String roomKey(String id){
    return "room:" + id;
  }

  // room:code:{code} 형식의 Redis 키 생성
  private String codeKey(String code){
    return "room:code:" + code;
  }

  @Override
  // Room 객체를 Redis에 저장 (TTL: 24시간)
  public Room save(Room room) {
    redis.opsForValue().set(roomKey(room.getId()), room, Duration.ofHours(24));
    return room;
  }

  @Override
  // roomId로 Redis에서 Room 객체 조회
  public Optional<Room> findById(String id) {
    Object v = redis.opsForValue().get(roomKey(id));
    return Optional.ofNullable((Room) v);
  }

  @Override
  // 초대 코드(code)로 Redis에서 Room 조회 (code → roomId → Room)
  public Optional<Room> findByCode(String code) {
    String id = srt.opsForValue().get(codeKey(code));
    return id == null ? Optional.empty() : findById(id);
  }

  @Override
  // code와 roomId를 매핑하여 Redis에 저장 (code → roomId)
  public void setCodeMapping(String code, String roomId, long ttlSeconds) {
    srt.opsForValue().set(codeKey(code), roomId, Duration.ofSeconds(ttlSeconds));
  }
}
