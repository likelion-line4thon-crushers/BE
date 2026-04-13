package line4thon.boini.presenter.pdf.dto.event;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CompleteEventData {

    private String pdfId;
    private int totalPages;
    private String status;
}
