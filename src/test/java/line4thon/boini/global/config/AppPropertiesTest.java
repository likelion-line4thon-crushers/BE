package line4thon.boini.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class AppPropertiesTest {
    @Test
    void fontDefaults() {
        AppProperties props = new AppProperties();
        assertThat(props.getOffice().getFcListPath()).isEqualTo("fc-list");
        assertThat(props.getOffice().getFcMatchPath()).isEqualTo("fc-match");
        assertThat(props.getFonts().getMaxFileBytes()).isEqualTo(15_728_640L);
        assertThat(props.getFonts().getMaxCount()).isEqualTo(20);
        assertThat(props.getFonts().getMaxTotalBytes()).isEqualTo(62_914_560L);
    }
}
