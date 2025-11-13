package line4thon.boini.presenter.room.dto.request;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor

public class RefreshPresenterTokenRequest {
  private String presenterKey;
}
