package line4thon.boini.presenter.pdf.service.font;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import line4thon.boini.global.config.AppProperties;
import line4thon.boini.presenter.pdf.dto.FontEntry;
import line4thon.boini.presenter.pdf.model.FontStatus;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PresentationFontAnalysisServiceTest {
    @TempDir Path tempDir;

    private Path pptxWithFont(String family) throws Exception {
        Path pptx = tempDir.resolve("deck-" + family.hashCode() + ".pptx");
        try (XMLSlideShow ppt = new XMLSlideShow()) {
            XSLFTextRun run = ppt.createSlide().createTextBox().addNewTextParagraph().addNewTextRun();
            run.setText("text"); run.setFontFamily(family);
            try (OutputStream out = Files.newOutputStream(pptx)) { ppt.write(out); }
        }
        return pptx;
    }

    private InstalledFontProvider stub(Set<String> families) {
        return new InstalledFontProvider(new AppProperties()) {
            @Override public Set<String> installedFamilies() { return families; }
            @Override public boolean isAvailable() { return true; }
        };
    }

    private InstalledFontProvider unavailableStub() {
        return new InstalledFontProvider(new AppProperties()) {
            @Override public Set<String> installedFamilies() { return Set.of(); }
            @Override public boolean isAvailable() { return false; }
        };
    }

    @Test
    void flagsMissingFont() throws Exception {
        FontEntry entry = new PresentationFontAnalysisService(stub(Set.of("arial")))
            .analyze(pptxWithFont("Malgun Gothic")).stream()
            .filter(e -> e.name().equals("Malgun Gothic")).findFirst().orElseThrow();
        assertThat(entry.status()).isEqualTo(FontStatus.MISSING);
    }

    @Test
    void marksInstalledFontAvailable() throws Exception {
        FontEntry entry = new PresentationFontAnalysisService(stub(Set.of("arial")))
            .analyze(pptxWithFont("Arial")).stream()
            .filter(e -> e.name().equals("Arial")).findFirst().orElseThrow();
        assertThat(entry.status()).isEqualTo(FontStatus.AVAILABLE);
    }

    @Test
    void returnsEmptyWhenProviderUnavailable() throws Exception {
        assertThat(new PresentationFontAnalysisService(unavailableStub())
            .analyze(pptxWithFont("Malgun Gothic"))).isEmpty();
    }

    @Test
    void marksExtraAvailableFontAvailable() throws Exception {
        FontEntry entry = new PresentationFontAnalysisService(stub(Set.of()))
            .analyze(pptxWithFont("Malgun Gothic"), Set.of(FontNames.normalize("Malgun Gothic"))).stream()
            .filter(e -> e.name().equals("Malgun Gothic")).findFirst().orElseThrow();
        assertThat(entry.status()).isEqualTo(FontStatus.AVAILABLE);
    }
}
