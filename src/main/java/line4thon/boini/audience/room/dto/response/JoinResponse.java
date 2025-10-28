package line4thon.boini.audience.room.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class JoinResponse {
  private final String roomId;
  private final String code;
  private final String audienceId;
  private final String audienceToken;
}
