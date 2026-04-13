package line4thon.boini.presenter.pdf.dto.event;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ErrorEventData {

    private String pdfId;
    private int pageIndex;
    private String message;
    private String code;
}
