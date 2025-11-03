package line4thon.boini.presenter.image.dto.response;

import java.util.List;
import line4thon.boini.presenter.image.dto.ThumbnailDto;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UploadPagesResponse {
  private final String deckId;
  private final int totalPages;
  private final String firstPageOriginalUrl;
  private final List<ThumbnailDto> thumbnails;
}