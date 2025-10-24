package line4thon.boini.presenter.room.dto.request;

import jakarta.validation.constraints.NotNull;
import line4thon.boini.presenter.room.model.RoomOptions;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateRoomRequest {

  @NotNull
  private RoomOptions options;
}