package line4thon.boini.presenter.image.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class OriginalUrlResponse {
  private final String roomId;
  private final String deckId;
  private final int page;
  private final String originalUrl;
}