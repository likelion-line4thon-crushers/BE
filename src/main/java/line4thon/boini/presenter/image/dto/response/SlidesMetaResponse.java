package line4thon.boini.presenter.image.dto.response;

import java.util.List;
import line4thon.boini.presenter.image.dto.ThumbnailDto;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SlidesMetaResponse {
  private final String roomId;
  private final String deckId;
  private final int totalPages;

  @com.fasterxml.jackson.annotation.JsonProperty("thumbnailUrl")
  private final List<ThumbnailDto> slides;
}