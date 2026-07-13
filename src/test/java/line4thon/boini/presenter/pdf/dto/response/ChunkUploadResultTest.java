package line4thon.boini.presenter.pdf.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ChunkUploadResultTest {
    @Test
    void needsFontsIsCompleteAndCarriesReport() {
        NeedsFontsResponse payload = NeedsFontsResponse.of("u1", List.of());
        ChunkUploadResult result = ChunkUploadResult.needsFonts(payload);
        assertThat(result.complete()).isTrue();
        assertThat(result.needsFonts()).isSameAs(payload);
        assertThat(result.assembled()).isNull();
    }
}
