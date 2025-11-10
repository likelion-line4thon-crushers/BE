package line4thon.boini.audience.option.dto.request;

import lombok.*;

@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
public class OptionRequest {
    private String sticker;
    private String question;
    private String feedback;
}
