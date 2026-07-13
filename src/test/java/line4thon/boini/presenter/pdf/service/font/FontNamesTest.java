package line4thon.boini.presenter.pdf.service.font;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class FontNamesTest {
    @Test
    void normalizesCaseAndWhitespace() {
        assertThat(FontNames.normalize("  Malgun  Gothic ")).isEqualTo("malgun gothic");
    }

    @Test
    void stripsTrailingStyleWords() {
        assertThat(FontNames.normalize("Arial Bold")).isEqualTo("arial");
        assertThat(FontNames.normalize("Noto Sans CJK KR Regular")).isEqualTo("noto sans cjk kr");
    }

    @Test
    void keepsKoreanNames() {
        assertThat(FontNames.normalize("맑은 고딕")).isEqualTo("맑은 고딕");
    }
}
