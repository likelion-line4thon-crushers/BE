package line4thon.boini.presenter.room.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CreateRoomResponse {
  private final String roomId;
  private final String code;
  private final String joinUrl;
  private final String wsUrl;
  private final Integer count;
  private Integer totalPages;
  private final String qrPngBase64;
  private final String presenterToken;
  private final String presenterKey;
  private final String deckId;
}
