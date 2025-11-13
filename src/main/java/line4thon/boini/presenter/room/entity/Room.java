package line4thon.boini.presenter.room.entity;

import lombok.*;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Room {
  private String id;
  private String code;
  private Integer count;
  private RoomOptions options;
  private Instant createdAt;
}