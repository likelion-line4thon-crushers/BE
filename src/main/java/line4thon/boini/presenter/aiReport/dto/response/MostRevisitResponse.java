package line4thon.boini.presenter.aiReport.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MostRevisitResponse {
    private int slide;
    private int totalRevisits;
    private int totalAudienceCount;
    private int uniqueUsers;
    private int multiRevisitUsers;
}
