package line4thon.boini.presenter.pdf.dto;

import line4thon.boini.presenter.pdf.model.FontStatus;

/**
 * @param substitute 누락(MISSING) 폰트가 업로드 없이 변환될 때 서버가 실제로 사용할 대체 폰트 패밀리명
 *                   (fc-match 결과). 알 수 없거나 사용 가능한 폰트면 null.
 */
public record FontEntry(String name, FontStatus status, boolean embedded, boolean installed, String substitute) {
    public FontEntry(String name, FontStatus status, boolean embedded, boolean installed) {
        this(name, status, embedded, installed, null);
    }
}
