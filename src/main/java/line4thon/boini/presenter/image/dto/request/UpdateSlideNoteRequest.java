package line4thon.boini.presenter.image.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UpdateSlideNoteRequest {

    @Size(max = 20000)
    private String notes;
}
