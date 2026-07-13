package line4thon.boini.presenter.pdf.dto.response;

import java.util.List;
import line4thon.boini.presenter.pdf.dto.FontEntry;

public record NeedsFontsResponse(String status, String uploadId, List<FontEntry> fontReport) {
    public static NeedsFontsResponse of(String uploadId, List<FontEntry> fontReport) {
        return new NeedsFontsResponse("NEEDS_FONTS", uploadId, fontReport);
    }
}
