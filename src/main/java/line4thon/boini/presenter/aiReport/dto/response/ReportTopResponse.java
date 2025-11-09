package line4thon.boini.presenter.aiReport.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportTopResponse {
    Long totalEmoji;
    Long totalQuestion;
    Long focusSlide;
}
