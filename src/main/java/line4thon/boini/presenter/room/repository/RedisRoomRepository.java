package line4thon.boini.presenter.room.repository;

import lombok.RequiredArgsConstructor;
import line4thon.boini.presenter.room.entity.Room;
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

  private String roomKey(String id){
    return "room:" + id;
  }

  private String codeKey(String code){
    return "room:code:" + code;
  }

  @Override
  public Room save(Room room) {
    redis.opsForValue().set(roomKey(room.getId()), room, Duration.ofHours(24));
    return room;
  }

  @Override
  public Optional<Room> findById(String id) {
    Object v = redis.opsForValue().get(roomKey(id));
    return Optional.ofNullable((Room) v);
  }

  @Override
  public Optional<Room> findByCode(String code) {
    String id = srt.opsForValue().get(codeKey(code));
    return id == null ? Optional.empty() : findById(id);
  }

  @Override
  public void setCodeMapping(String code, String roomId, long ttlSeconds) {
    srt.opsForValue().set(codeKey(code), roomId, Duration.ofSeconds(ttlSeconds));
  }
}
