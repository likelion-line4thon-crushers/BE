package line4thon.boini.audience.room.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
@AllArgsConstructor
public class RoomInfoResponse {
    private String roomId;
    private String currentPage;
    private String sessionStatus;
    private String maxPage;
    private String sticker;
    private String question;
    private String feedback;
    private String slideUnlock;
}
