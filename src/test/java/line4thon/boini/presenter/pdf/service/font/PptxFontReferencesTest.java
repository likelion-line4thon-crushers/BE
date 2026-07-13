package line4thon.boini.presenter.pdf.service.font;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
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

    @Test
    void excludesThemePerScriptFallbackFonts() {
        // A realistic fontScheme: primary <a:latin> are the fonts actually used; the many
        // <a:font script="..."> entries are per-script fallbacks and must NOT be reported.
        String themeXml = """
            <a:fontScheme name="Custom">
              <a:majorFont>
                <a:latin typeface="Yoon Mokryn"/>
                <a:ea typeface=""/>
                <a:cs typeface=""/>
                <a:font script="Jpan" typeface="游ゴシック Light"/>
                <a:font script="Hang" typeface="맑은 고딕"/>
                <a:font script="Hans" typeface="等线 Light"/>
                <a:font script="Thai" typeface="Angsana New"/>
              </a:majorFont>
              <a:minorFont>
                <a:latin typeface="210 GulgjighanGothicOTF Bold"/>
                <a:ea typeface=""/>
                <a:font script="Hang" typeface="맑은 고딕"/>
                <a:font script="Deva" typeface="Mangal"/>
              </a:minorFont>
            </a:fontScheme>
            """;
        Set<String> out = new LinkedHashSet<>();
        PptxFontReferences.addReferencedFonts(themeXml, out);

        assertThat(out).containsExactlyInAnyOrder("Yoon Mokryn", "210 GulgjighanGothicOTF Bold");
        assertThat(out).doesNotContain("맑은 고딕", "游ゴシック Light", "等线 Light", "Angsana New", "Mangal");
    }

    @Test
    void skipsThemeTokensAndEmpties() {
        String runXml = "<a:rPr><a:latin typeface=\"+mn-lt\"/><a:ea typeface=\"\"/>"
            + "<a:cs typeface=\"Arial\"/></a:rPr>";
        Set<String> out = new LinkedHashSet<>();
        PptxFontReferences.addReferencedFonts(runXml, out);

        assertThat(out).containsExactly("Arial");
    }
}
