package line4thon.boini.presenter.pdf.dto.response;

import java.util.List;
import line4thon.boini.presenter.pdf.dto.FontEntry;

/**
 * 폰트 업로드 결과.
 *
 * @param report           지금까지 업로드된 모든 폰트를 반영한 갱신 리포트
 * @param matched          targetFont 를 지정한 경우, 방금 업로드한 파일이 그 요구 폰트와 실제로 일치하는지.
 *                         지정하지 않았으면 null.
 * @param targetFont       클라이언트가 이 업로드로 채우려던 요구 폰트명(있으면)
 * @param uploadedFamilies 방금 업로드한 파일들의 내부 패밀리명(사용자에게 무엇이 올라갔는지 보여주기 위함)
 */
public record FontUploadResponse(
    List<FontEntry> report,
    Boolean matched,
    String targetFont,
    List<String> uploadedFamilies
) {}
