package line4thon.boini.presenter.pdf.service.font;

import java.awt.Font;
import java.io.ByteArrayInputStream;
import java.util.Locale;
import java.util.Set;
import line4thon.boini.global.common.exception.CustomException;
import line4thon.boini.global.config.AppProperties;
import line4thon.boini.presenter.pdf.exception.PdfErrorCode;
import org.springframework.stereotype.Component;

@Component
public class FontUploadValidator {
    private static final Set<String> ALLOWED = Set.of(".ttf", ".otf", ".ttc");
    private final AppProperties props;

    public FontUploadValidator(AppProperties props) { this.props = props; }

    public void validate(String originalName, byte[] bytes) {
        String lower = originalName == null ? "" : originalName.toLowerCase(Locale.ROOT);
        if (ALLOWED.stream().noneMatch(lower::endsWith)) {
            throw new CustomException(PdfErrorCode.INVALID_FONT_FILE);
        }
        if (bytes.length > props.getFonts().getMaxFileBytes()) {
            throw new CustomException(PdfErrorCode.FONT_TOO_LARGE);
        }
        try {
            Font.createFont(Font.TRUETYPE_FONT, new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            throw new CustomException(PdfErrorCode.INVALID_FONT_FILE);
        }
    }
}
