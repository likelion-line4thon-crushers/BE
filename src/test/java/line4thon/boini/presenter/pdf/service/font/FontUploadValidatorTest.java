package line4thon.boini.presenter.pdf.service.font;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import line4thon.boini.global.common.exception.CustomException;
import line4thon.boini.global.config.AppProperties;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class FontUploadValidatorTest {
    private final FontUploadValidator validator = new FontUploadValidator(new AppProperties());

    @Test
    void rejectsUnsupportedExtension() {
        assertThatThrownBy(() -> validator.validate("evil.exe", new byte[] {1, 2, 3}))
            .isInstanceOf(CustomException.class);
    }

    @Test
    void rejectsOversizeFile() {
        assertThatThrownBy(() -> validator.validate("big.ttf", new byte[6 * 1024 * 1024]))
            .isInstanceOf(CustomException.class);
    }

    @Test
    void rejectsUnparseableFont() {
        assertThatThrownBy(() -> validator.validate("fake.ttf", "not a font".getBytes()))
            .isInstanceOf(CustomException.class);
    }

    @Test
    void acceptsRealFont() {
        byte[] ttf = TestFonts.anyTrueTypeFontBytes();
        Assumptions.assumeTrue(ttf != null, "no system TTF available");
        assertThatCode(() -> validator.validate("real.ttf", ttf)).doesNotThrowAnyException();
    }
}
