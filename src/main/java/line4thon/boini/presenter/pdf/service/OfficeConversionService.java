package line4thon.boini.presenter.pdf.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import line4thon.boini.global.common.exception.CustomException;
import line4thon.boini.global.config.AppProperties;
import line4thon.boini.presenter.pdf.exception.PdfErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class OfficeConversionService {

    private final AppProperties props;

    private static final List<String> SOFFICE_FALLBACK_PATHS = List.of(
        "/Applications/LibreOffice.app/Contents/MacOS/soffice",
        "/opt/homebrew/bin/soffice",
        "/usr/local/bin/soffice"
    );

    public Path convertToPdf(Path sourceFile) {
        Path outputDir = sourceFile.getParent().resolve("converted");
        Path userProfile = sourceFile.getParent().resolve("lo-profile-" + UUID.randomUUID());
        try {
            Files.createDirectories(outputDir);
            Files.createDirectories(userProfile);

            String sofficePath = resolveSofficePath(props.getOffice().getSofficePath());
            ProcessBuilder builder = new ProcessBuilder(List.of(
                sofficePath,
                "--headless",
                "--nologo",
                "--nofirststartwizard",
                "--convert-to",
                "pdf",
                "--outdir",
                outputDir.toString(),
                "-env:UserInstallation=" + userProfile.toUri(),
                sourceFile.toString()
            ));
            builder.redirectErrorStream(true);
            builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);

            Process process = start(builder);
            boolean finished = waitFor(process, Duration.ofSeconds(props.getOffice().getConversionTimeoutSeconds()));
            if (!finished) {
                process.destroyForcibly();
                log.error("[Office] 변환 타임아웃: file={}", sourceFile);
                throw new CustomException(PdfErrorCode.OFFICE_CONVERSION_FAILED);
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                log.error("[Office] 변환 실패: file={}, exitCode={}", sourceFile, exitCode);
                throw new CustomException(PdfErrorCode.OFFICE_CONVERSION_FAILED);
            }

            Path pdfPath = outputDir.resolve(stripExtension(sourceFile.getFileName().toString()) + ".pdf");
            if (!Files.exists(pdfPath)) {
                log.error("[Office] 변환 결과 PDF 없음: expected={}", pdfPath);
                throw new CustomException(PdfErrorCode.OFFICE_CONVERSION_FAILED);
            }

            log.info("[Office] PDF 변환 완료: source={}, pdf={}", sourceFile.getFileName(), pdfPath.getFileName());
            return pdfPath;
        } catch (CustomException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("[Office] 변환 중 예외: file={}", sourceFile, e);
            throw new CustomException(PdfErrorCode.OFFICE_CONVERSION_FAILED);
        }
    }

    protected Process start(ProcessBuilder builder) throws IOException {
        return builder.start();
    }

    protected boolean waitFor(Process process, Duration timeout) throws InterruptedException {
        return process.waitFor(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    protected boolean isExecutable(Path path) {
        return Files.isRegularFile(path) && Files.isExecutable(path);
    }

    private String resolveSofficePath(String configuredPath) {
        String trimmed = configuredPath == null ? "" : configuredPath.trim();
        if (trimmed.isEmpty()) {
            trimmed = "soffice";
        }

        Path configured = Paths.get(trimmed);
        if (configured.isAbsolute()) {
            if (isExecutable(configured)) {
                return configured.toString();
            }
            log.error("[Office] LibreOffice 실행 파일을 찾을 수 없습니다: configuredPath={}", configured);
            throw new CustomException(PdfErrorCode.OFFICE_CONVERSION_FAILED);
        }

        List<String> candidatePaths = new ArrayList<>();
        candidatePaths.add(trimmed);
        candidatePaths.addAll(SOFFICE_FALLBACK_PATHS);

        for (String candidate : candidatePaths) {
            Path candidatePath = Paths.get(candidate);
            if (candidatePath.isAbsolute() && isExecutable(candidatePath)) {
                return candidatePath.toString();
            }
        }

        Path executableFromPath = findExecutableOnPath(trimmed);
        if (executableFromPath != null) {
            return executableFromPath.toString();
        }

        log.error(
            "[Office] LibreOffice 실행 파일을 찾을 수 없습니다: configuredPath={}, fallbackPaths={}",
            trimmed,
            SOFFICE_FALLBACK_PATHS
        );
        throw new CustomException(PdfErrorCode.OFFICE_CONVERSION_FAILED);
    }

    private Path findExecutableOnPath(String command) {
        if (command.contains("/") || command.contains("\\")) {
            Path relativePath = Paths.get(command);
            return isExecutable(relativePath) ? relativePath : null;
        }

        String pathEnv = getPathEnv();
        if (pathEnv == null || pathEnv.isBlank()) {
            return null;
        }

        for (String dir : pathEnv.split(java.io.File.pathSeparator)) {
            if (dir.isBlank()) {
                continue;
            }
            Path candidate = Paths.get(dir).resolve(command);
            if (isExecutable(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    protected String getPathEnv() {
        return System.getenv("PATH");
    }

    private String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}
