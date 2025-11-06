package line4thon.boini.presenter.page.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SlideAudienceCountResponse {
    private int frontCount;
    private int currentCount;
    private int backCount;
}