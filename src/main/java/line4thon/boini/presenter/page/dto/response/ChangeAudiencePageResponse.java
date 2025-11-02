package line4thon.boini.presenter.page.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class ChangeAudiencePageResponse {
    private String audienceId;
    private Integer beforePage;
    private Integer changedPage;
    private LocalDateTime timestamp;
}