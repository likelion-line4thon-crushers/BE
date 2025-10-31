package line4thon.boini.audience.sticker.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StickerResponse {
    private Integer emoji;
    private LocalDateTime created_at;
    private double x;
    private double y;
    private Integer slide;
}
