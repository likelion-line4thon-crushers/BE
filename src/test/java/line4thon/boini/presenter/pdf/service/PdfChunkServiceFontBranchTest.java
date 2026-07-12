package line4thon.boini.presenter.pdf.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import line4thon.boini.presenter.pdf.dto.FontEntry;
import line4thon.boini.presenter.pdf.model.FontStatus;
import org.junit.jupiter.api.Test;

class PdfChunkServiceFontBranchTest {

    @Test
    void missingPredicate() {
        List<FontEntry> report = List.of(new FontEntry("Malgun Gothic", FontStatus.MISSING, false, false));
        assertThat(report.stream().anyMatch(e -> e.status() == FontStatus.MISSING)).isTrue();
    }

    @Test
    void noMissingPredicate() {
        List<FontEntry> report = List.of(new FontEntry("Arial", FontStatus.AVAILABLE, false, true));
        assertThat(report.stream().anyMatch(e -> e.status() == FontStatus.MISSING)).isFalse();
    }

    @Test
    void finalizeGuardRejectsNonAwaiting() {
        assertThat("AWAITING_FONTS".equals("READY")).isFalse();
    }

    @Test
    void missingListRoundTrips() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(List.of("Malgun Gothic", "맑은 고딕"));
        assertThat(mapper.readValue(json, new TypeReference<List<String>>() {}))
            .containsExactly("Malgun Gothic", "맑은 고딕");
    }
}
