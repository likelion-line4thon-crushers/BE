package line4thon.boini.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class AppPropertiesTest {
    @Test
    void fontDefaults() {
        AppProperties props = new AppProperties();
        assertThat(props.getOffice().getFcListPath()).isEqualTo("fc-list");
        assertThat(props.getFonts().getMaxFileBytes()).isEqualTo(5_242_880L);
        assertThat(props.getFonts().getMaxCount()).isEqualTo(20);
        assertThat(props.getFonts().getMaxTotalBytes()).isEqualTo(52_428_800L);
    }
}
