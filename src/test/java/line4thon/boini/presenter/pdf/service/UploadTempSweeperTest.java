package line4thon.boini.presenter.pdf.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import line4thon.boini.global.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UploadTempSweeperTest {
    @TempDir Path tempDir;

    @Test
    void deletesStaleKeepsFresh() throws Exception {
        AppProperties props = new AppProperties();
        props.getPdf().setTempDir(tempDir.toString());
        Path stale = Files.createDirectories(tempDir.resolve("old"));
        Path fresh = Files.createDirectories(tempDir.resolve("new"));
        Files.setLastModifiedTime(stale, FileTime.from(Instant.now().minus(2, ChronoUnit.HOURS)));

        new UploadTempSweeper(props).sweep();

        assertThat(Files.exists(stale)).isFalse();
        assertThat(Files.exists(fresh)).isTrue();
    }
}
