package line4thon.boini.presenter.page.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SlideAudienceCountResponse {
    private Long frontCount;
    private Long currentCount;
    private Long backCount;
}