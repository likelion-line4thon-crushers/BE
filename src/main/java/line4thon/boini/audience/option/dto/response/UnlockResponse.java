package line4thon.boini.audience.option.dto.response;

import lombok.*;

@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
public class UnlockResponse {
    private String maxRevealedPage;
    private String revealAllSlides;
    private String totalPages;
}
