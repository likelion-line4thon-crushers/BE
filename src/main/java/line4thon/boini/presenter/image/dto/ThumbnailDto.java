package line4thon.boini.presenter.image.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class ThumbnailDto {
  private int page;

  @com.fasterxml.jackson.annotation.JsonProperty("thumbnailUrl")
  private String thumbnailUrl;
}