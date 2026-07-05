package line4thon.boini.audience.question.dto.requeset;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record QuestionLikeRequest(
    @NotBlank String questionId,
    @NotBlank String audienceId,
    @NotNull Boolean liked
) {}
