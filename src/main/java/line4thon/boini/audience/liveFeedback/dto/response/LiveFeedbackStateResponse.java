package line4thon.boini.audience.liveFeedback.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 피드백 상태 모델
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LiveFeedbackStateResponse {
    private String priority; // 1 or 2
    private String feedbackMessage;
}
