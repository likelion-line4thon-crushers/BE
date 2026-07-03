package line4thon.boini.presenter.aiReport.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RevisitSlideRankResponse {
    private int slideNumber;
    private int uniqueUsers;
    private int totalRevisits;
    private int multiRevisitUsers;
}
