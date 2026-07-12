package line4thon.boini.presenter.pdf.service.font;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import line4thon.boini.global.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class InstalledFontProvider {
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private final AppProperties props;
    private volatile Set<String> cached;
    private volatile Instant cachedAt = Instant.EPOCH;

    public InstalledFontProvider(AppProperties props) { this.props = props; }

    public Set<String> installedFamilies() {
        Set<String> snap = cached;
        if (snap != null && Duration.between(cachedAt, Instant.now()).compareTo(CACHE_TTL) < 0) return snap;
        Set<String> families = query();
        cached = families; cachedAt = Instant.now();
        return families;
    }

    private Set<String> query() {
        Path binary = resolveBinary(props.getOffice().getFcListPath());
        if (binary == null) {
            log.warn("[Font] fc-list 실행 파일을 찾지 못해 설치 폰트 조회를 건너뜁니다.");
            return Set.of();
        }
        try {
            ProcessBuilder builder = new ProcessBuilder(List.of(binary.toString(), ":", "family"));
            Process process = start(builder);
            Set<String> families = new LinkedHashSet<>();
            try (BufferedReader r = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    for (String family : line.split(",")) {
                        String n = FontNames.normalize(family);
                        if (!n.isEmpty()) families.add(n);
                    }
                }
            }
            process.waitFor();
            return families;
        } catch (IOException e) {
            log.warn("[Font] fc-list 실행 실패", e);
            return Set.of();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Set.of();
        }
    }

    private Path resolveBinary(String configured) {
        String trimmed = configured == null || configured.isBlank() ? "fc-list" : configured.trim();
        Path direct = Paths.get(trimmed);
        if (direct.isAbsolute()) return isExecutable(direct) ? direct : null;
        String pathEnv = getPathEnv();
        if (pathEnv == null || pathEnv.isBlank()) return null;
        for (String dir : pathEnv.split(java.io.File.pathSeparator)) {
            if (dir.isBlank()) continue;
            Path candidate = Paths.get(dir).resolve(trimmed);
            if (isExecutable(candidate)) return candidate;
        }
        return null;
    }

    protected Process start(ProcessBuilder builder) throws IOException { return builder.start(); }
    protected boolean isExecutable(Path p) { return Files.isRegularFile(p) && Files.isExecutable(p); }
    protected String getPathEnv() { return System.getenv("PATH"); }
}
