package line4thon.boini.presenter.room.dto.request;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor

public class RefreshPresenterTokenRequest {
  private String presenterKey;  // 방 생성 시 받은 키 (원문)
}
