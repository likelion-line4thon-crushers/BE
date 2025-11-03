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
  private final String presenterToken;   // 발표자 접속용 JWT
  private final String presenterKey;     // 재발급용 1회 안내 키(플레인) — 프론트만 저장
  private final String deckId;
}
