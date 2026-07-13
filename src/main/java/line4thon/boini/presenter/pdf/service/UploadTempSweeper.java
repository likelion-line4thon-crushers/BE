package line4thon.boini.presenter.pdf.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import line4thon.boini.global.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class UploadTempSweeper {
    private static final Duration MAX_AGE = Duration.ofHours(1);
    private final AppProperties props;

    @Scheduled(fixedDelay = 3_600_000L)
    public void sweep() {
        Path root = Paths.get(props.getPdf().getTempDir());
        if (!Files.isDirectory(root)) return;
        try (var dirs = Files.list(root)) {
            dirs.filter(Files::isDirectory).forEach(this::deleteIfStale);
        } catch (IOException e) {
            log.warn("[Sweep] 임시 업로드 정리 실패: root={}", root, e);
        }
    }

    private void deleteIfStale(Path dir) {
        try {
            Instant modified = Files.getLastModifiedTime(dir).toInstant();
            if (Duration.between(modified, Instant.now()).compareTo(MAX_AGE) <= 0) return;
            try (var walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.delete(p); } catch (IOException ignored) {}
                });
            }
            log.info("[Sweep] 오래된 업로드 삭제: {}", dir);
        } catch (IOException e) {
            log.warn("[Sweep] 삭제 실패: {}", dir, e);
        }
    }
}
