package line4thon.boini.presenter.room.repository;

import line4thon.boini.presenter.room.entity.Room;
import java.util.Optional;

public interface RoomRepository {
  Room save(Room room);
  Optional<Room> findById(String id);
  Optional<Room> findByCode(String code);
  void setCodeMapping(String code, String roomId, long ttlSeconds);
}
