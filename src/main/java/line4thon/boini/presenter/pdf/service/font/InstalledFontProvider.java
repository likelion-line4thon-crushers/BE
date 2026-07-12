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
    private volatile Query cached;
    private volatile Instant cachedAt = Instant.EPOCH;

    public InstalledFontProvider(AppProperties props) { this.props = props; }

    public Set<String> installedFamilies() {
        return current().families();
    }

    /**
     * 마지막 조회에서 fc-list 바이너리를 실제로 실행할 수 있었는지 여부.
     * false: 바이너리를 찾지 못했거나 프로세스가 IOException 으로 실패한 경우.
     * true : 바이너리가 실행된 경우(출력이 비어 있어도 true).
     * 이 값이 false 이면 설치 폰트를 알 수 없으므로, 상위(analyze)는 폰트 누락 판단을 건너뛴다.
     */
    public boolean isAvailable() {
        return current().available();
    }

    private Query current() {
        Query snap = cached;
        if (snap != null && Duration.between(cachedAt, Instant.now()).compareTo(CACHE_TTL) < 0) return snap;
        Query result = query();
        cached = result; cachedAt = Instant.now();
        return result;
    }

    private record Query(Set<String> families, boolean available) {}

    private Query query() {
        Path binary = resolveBinary(props.getOffice().getFcListPath());
        if (binary == null) {
            log.warn("[Font] fc-list 실행 파일을 찾지 못해 설치 폰트 조회를 건너뜁니다.");
            return new Query(Set.of(), false);
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
            return new Query(families, true);
        } catch (IOException e) {
            log.warn("[Font] fc-list 실행 실패", e);
            return new Query(Set.of(), false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Query(Set.of(), false);
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
