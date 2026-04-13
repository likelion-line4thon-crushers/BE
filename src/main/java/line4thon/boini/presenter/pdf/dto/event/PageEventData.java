package line4thon.boini.presenter.pdf.dto.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PageEventData {

    private String pdfId;
    private int pageIndex;
    private int totalPages;
    private String imageUrl;
    private String format;
    private Integer width;
    private Integer height;
    private boolean canStartSession;
}
