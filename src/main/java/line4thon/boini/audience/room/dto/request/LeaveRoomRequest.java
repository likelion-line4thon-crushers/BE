package line4thon.boini.audience.room.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LeaveRoomRequest {
    private String AudienceId;
    private String AudienceJWT;
}
