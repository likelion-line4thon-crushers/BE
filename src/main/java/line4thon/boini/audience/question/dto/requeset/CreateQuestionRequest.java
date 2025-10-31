package line4thon.boini.audience.question.dto.requeset;

import jakarta.validation.constraints.*;

public record CreateQuestionRequest(
    @NotBlank String audienceId,
    @NotNull Integer slide,
    @NotBlank @Size(max = 500) String content,
    Long ts // optional client timestamp(ms)
) {}