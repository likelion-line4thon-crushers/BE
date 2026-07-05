package line4thon.boini.presenter.pdf.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PresentationFileTypeTest {

    @Test
    void detectsSupportedPresentationExtensions() {
        assertThat(PresentationFileType.fromFileName("demo.pdf")).contains(PresentationFileType.PDF);
        assertThat(PresentationFileType.fromFileName("demo.PPT")).contains(PresentationFileType.PPT);
        assertThat(PresentationFileType.fromFileName("demo.PPTX")).contains(PresentationFileType.PPTX);
    }

    @Test
    void rejectsUnsupportedExtensions() {
        assertThat(PresentationFileType.fromFileName("demo.key")).isEmpty();
        assertThat(PresentationFileType.fromFileName("demo")).isEmpty();
        assertThat(PresentationFileType.fromFileName(null)).isEmpty();
    }
}
