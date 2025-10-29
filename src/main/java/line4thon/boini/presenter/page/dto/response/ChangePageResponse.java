package line4thon.boini.presenter.page.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class ChangePageResponse {
    private Integer beforePage;
    private Integer changedPage;
    private LocalDateTime timestamp;
}
