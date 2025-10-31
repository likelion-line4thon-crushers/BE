package line4thon.boini.audience.sticker.dto.request;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StickerRequest {
    private Integer emoji;
    private String audienceID;
    private LocalDateTime created_at;
    private double x;
    private double y;
    private Integer slide;
}
