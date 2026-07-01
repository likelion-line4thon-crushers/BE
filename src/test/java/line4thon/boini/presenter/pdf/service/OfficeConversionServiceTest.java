package line4thon.boini.presenter.pdf.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import line4thon.boini.global.common.exception.CustomException;
import line4thon.boini.global.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OfficeConversionServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void returnsConvertedPdfPathWhenLibreOfficeSucceeds() throws IOException {
        AppProperties props = new AppProperties();
        Path executable = tempDir.resolve("bin/soffice-test");
        props.getOffice().setSofficePath(executable.toString());
        OfficeConversionService service = new FakeOfficeConversionService(props, 0, true, false)
            .withExecutable(executable);
        Path source = Files.writeString(tempDir.resolve("deck.pptx"), "demo");

        Path converted = service.convertToPdf(source);

        assertThat(converted).exists();
        assertThat(converted.getFileName().toString()).isEqualTo("deck.pdf");
    }

    @Test
    void resolvesConfiguredCommandFromPath() throws IOException {
        AppProperties props = new AppProperties();
        props.getOffice().setSofficePath("soffice-test");
        Path binDir = tempDir.resolve("bin");
        Path executable = binDir.resolve("soffice-test");
        FakeOfficeConversionService service = new FakeOfficeConversionService(props, 0, true, false)
            .withPathEnv(binDir.toString())
            .withExecutable(executable);
        Path source = Files.writeString(tempDir.resolve("deck.pptx"), "demo");

        service.convertToPdf(source);

        assertThat(service.startedCommand()).isEqualTo(executable.toString());
    }

    @Test
    void resolvesMacLibreOfficeFallbackWhenDefaultCommandIsNotOnPath() throws IOException {
        AppProperties props = new AppProperties();
        Path macFallback = Path.of("/Applications/LibreOffice.app/Contents/MacOS/soffice");
        FakeOfficeConversionService service = new FakeOfficeConversionService(props, 0, true, false)
            .withExecutable(macFallback);
        Path source = Files.writeString(tempDir.resolve("deck.pptx"), "demo");

        service.convertToPdf(source);

        assertThat(service.startedCommand()).isEqualTo(macFallback.toString());
    }

    @Test
    void throwsWhenLibreOfficeFails() throws IOException {
        AppProperties props = new AppProperties();
        Path executable = tempDir.resolve("bin/soffice-test");
        props.getOffice().setSofficePath(executable.toString());
        OfficeConversionService service = new FakeOfficeConversionService(props, 1, false, false)
            .withExecutable(executable);
        Path source = Files.writeString(tempDir.resolve("deck.ppt"), "demo");

        assertThatThrownBy(() -> service.convertToPdf(source)).isInstanceOf(CustomException.class);
    }

    @Test
    void throwsWhenLibreOfficeTimesOut() throws IOException {
        AppProperties props = new AppProperties();
        Path executable = tempDir.resolve("bin/soffice-test");
        props.getOffice().setSofficePath(executable.toString());
        OfficeConversionService service = new FakeOfficeConversionService(props, 0, false, true)
            .withExecutable(executable);
        Path source = Files.writeString(tempDir.resolve("deck.ppt"), "demo");

        assertThatThrownBy(() -> service.convertToPdf(source)).isInstanceOf(CustomException.class);
    }

    @Test
    void throwsBeforeStartingWhenLibreOfficeCannotBeResolved() throws IOException {
        AppProperties props = new AppProperties();
        FakeOfficeConversionService service = new FakeOfficeConversionService(props, 0, true, false);
        Path source = Files.writeString(tempDir.resolve("deck.ppt"), "demo");

        assertThatThrownBy(() -> service.convertToPdf(source)).isInstanceOf(CustomException.class);
        assertThat(service.startedCommand()).isNull();
    }

    private static class FakeOfficeConversionService extends OfficeConversionService {
        private final int exitCode;
        private final boolean createPdf;
        private final boolean timeout;
        private final Set<Path> executables = new HashSet<>();
        private String pathEnv;
        private String startedCommand;

        FakeOfficeConversionService(
            AppProperties props,
            int exitCode,
            boolean createPdf,
            boolean timeout
        ) {
            super(props);
            this.exitCode = exitCode;
            this.createPdf = createPdf;
            this.timeout = timeout;
        }

        FakeOfficeConversionService withExecutable(Path executable) {
            executables.add(executable);
            return this;
        }

        FakeOfficeConversionService withPathEnv(String pathEnv) {
            this.pathEnv = pathEnv;
            return this;
        }

        String startedCommand() {
            return startedCommand;
        }

        @Override
        protected Process start(ProcessBuilder builder) throws IOException {
            startedCommand = builder.command().get(0);
            if (createPdf) {
                Path outputDir = Path.of(builder.command().get(7));
                Path source = Path.of(builder.command().get(9));
                String name = source.getFileName().toString().replaceFirst("\\.[^.]+$", ".pdf");
                Files.createDirectories(outputDir);
                Files.writeString(outputDir.resolve(name), "%PDF");
            }
            return new FakeProcess(exitCode);
        }

        @Override
        protected boolean waitFor(Process process, Duration timeout) {
            return !this.timeout;
        }

        @Override
        protected boolean isExecutable(Path path) {
            return executables.contains(path);
        }

        @Override
        protected String getPathEnv() {
            return pathEnv;
        }
    }

    private static class FakeProcess extends Process {
        private final int exitCode;

        FakeProcess(int exitCode) {
            this.exitCode = exitCode;
        }

        @Override
        public OutputStream getOutputStream() {
            return OutputStream.nullOutputStream();
        }

        @Override
        public java.io.InputStream getInputStream() {
            return java.io.InputStream.nullInputStream();
        }

        @Override
        public java.io.InputStream getErrorStream() {
            return java.io.InputStream.nullInputStream();
        }

        @Override
        public int waitFor() {
            return exitCode;
        }

        @Override
        public int exitValue() {
            return exitCode;
        }

        @Override
        public void destroy() {
        }
    }
}
