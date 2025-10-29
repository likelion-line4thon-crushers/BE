package line4thon.boini.presenter.page.dto.request;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChangePageRequest {
    private Integer beforePage;
    private Integer changedPage;
}
