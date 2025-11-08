package line4thon.boini.presenter.aiReport.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MostRevisitResponse {
    private int slide;              // 슬라이드 번호
    private int totalRevisits;      // 총 재방문 수
    private int totalAudienceCount;
    private int uniqueUsers;        // 재방문한 사용자 수
    private int multiRevisitUsers;  // 2번 이상 다시 본 사용자 수
}
