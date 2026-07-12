// UploadedFontsIntegrationTest.java
package line4thon.boini.presenter.pdf.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import line4thon.boini.global.config.AppProperties;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UploadedFontsIntegrationTest {
    @TempDir Path tempDir;

    @Test
    void convertsKoreanDeckAndExtractsKoreanText() throws Exception {
        assumeTrue(binaryExists("soffice") || binaryExists("/opt/homebrew/bin/soffice"),
            "LibreOffice not installed");

        Path pptx = tempDir.resolve("deck.pptx");
        try (XMLSlideShow ppt = new XMLSlideShow()) {
            XSLFTextRun run = ppt.createSlide().createTextBox().addNewTextParagraph().addNewTextRun();
            run.setText("안녕하세요 발표"); run.setFontFamily("Noto Sans CJK KR");
            try (var out = Files.newOutputStream(pptx)) { ppt.write(out); }
        }

        Path pdf = new OfficeConversionService(new AppProperties()).convertToPdf(pptx, null);

        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            assertThat(new PDFTextStripper().getText(doc)).contains("안녕하세요");
        }
    }

    @Test
    void convertsWithInjectedUploadedFont() throws Exception {
        assumeTrue(binaryExists("soffice") || binaryExists("/opt/homebrew/bin/soffice"),
            "LibreOffice not installed");

        Path systemFont = anySystemFont();
        assumeTrue(systemFont != null, "No system TTF available");

        Path fontDir = tempDir.resolve("fonts");
        Files.createDirectories(fontDir);
        Files.copy(systemFont, fontDir.resolve(systemFont.getFileName()));

        Path pptx = tempDir.resolve("injected.pptx");
        try (XMLSlideShow ppt = new XMLSlideShow()) {
            XSLFTextRun run = ppt.createSlide().createTextBox().addNewTextParagraph().addNewTextRun();
            run.setText("hello 발표"); run.setFontFamily("Arial");
            try (var out = Files.newOutputStream(pptx)) { ppt.write(out); }
        }

        // fontDir 를 넘기면 applyFontconfig 가 FONTCONFIG_FILE 을 설정하는 경로를 end-to-end 로 구동한다.
        Path pdf = new OfficeConversionService(new AppProperties()).convertToPdf(pptx, fontDir);

        assertThat(Files.exists(pdf)).isTrue();
        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            assertThat(doc.getNumberOfPages()).isGreaterThanOrEqualTo(1);
        }
    }

    private Path anySystemFont() {
        for (Path p : new Path[] {
            Path.of("/System/Library/Fonts/Supplemental/Arial.ttf"),
            Path.of("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"),
            Path.of("/Library/Fonts/Arial.ttf")}) {
            if (Files.isRegularFile(p)) return p;
        }
        return null;
    }

    private boolean binaryExists(String path) {
        try { return new ProcessBuilder(path, "--version").start().waitFor() == 0; }
        catch (Exception e) { return Files.isExecutable(Path.of(path)); }
    }
}
