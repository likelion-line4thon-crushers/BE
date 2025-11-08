package line4thon.boini.presenter.aiReport.dto.response;

import lombok.*;


@Builder
public record MostReactionStickerResponse(
        int emoji,
        int topSlide,
        long topCount,
        int secondSlide,
        long secondCount
) {}
