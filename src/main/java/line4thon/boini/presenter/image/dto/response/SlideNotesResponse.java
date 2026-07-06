package line4thon.boini.presenter.image.dto.response;

import java.util.List;
import line4thon.boini.presenter.image.dto.SlideNoteDto;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SlideNotesResponse {
    private final String roomId;
    private final String deckId;
    private final List<SlideNoteDto> notes;
}
