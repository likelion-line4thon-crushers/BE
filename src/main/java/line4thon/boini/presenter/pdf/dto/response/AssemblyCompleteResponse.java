package line4thon.boini.presenter.pdf.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AssemblyCompleteResponse {

    private String status;
    private String uploadId;
    private String pdfId;
    private String fileName;
    private int totalPages;
    private String streamUrl;
}
