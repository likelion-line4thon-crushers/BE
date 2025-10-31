package line4thon.boini.presenter.room.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateRoomRequest {
  @NotNull
  private Integer count;

  @NotNull
  private Integer totalPages;
}