package line4thon.boini.audience.sticker.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StickerLoadResponse {
    private Integer emoji;
    private double x;
    private double y;
    private Integer slide;
}
