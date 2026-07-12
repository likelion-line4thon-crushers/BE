package line4thon.boini.presenter.pdf.service.font;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PptxFontReferencesTest {
    @TempDir Path tempDir;

    @Test
    void findsRunTypeface() throws Exception {
        Path pptx = tempDir.resolve("deck.pptx");
        try (XMLSlideShow ppt = new XMLSlideShow()) {
            XSLFTextRun run = ppt.createSlide().createTextBox().addNewTextParagraph().addNewTextRun();
            run.setText("안녕하세요");
            run.setFontFamily("Malgun Gothic");
            try (OutputStream out = Files.newOutputStream(pptx)) { ppt.write(out); }
        }
        PptxFontReferences.Result result = PptxFontReferences.read(pptx);
        assertThat(result.referenced()).contains("Malgun Gothic");
        assertThat(result.embedded()).isEmpty();
    }
}
