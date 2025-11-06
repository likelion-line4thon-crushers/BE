package line4thon.boini.audience.room.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LeaveRoomResponse {
    private String roomId;
    private String AudienceId;
    private String AudienceJWT;
}
