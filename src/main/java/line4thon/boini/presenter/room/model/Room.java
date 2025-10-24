package line4thon.boini.presenter.room.model;

import lombok.*;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Room {
  private String id;          // UUID
  private String code;        // 6자리 초대코드
  private RoomOptions options;
  private Instant createdAt;
}