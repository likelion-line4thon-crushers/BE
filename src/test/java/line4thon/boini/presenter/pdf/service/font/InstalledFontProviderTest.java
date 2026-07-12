package line4thon.boini.presenter.pdf.service.font;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Set;
import line4thon.boini.global.config.AppProperties;
import org.junit.jupiter.api.Test;

class InstalledFontProviderTest {
    @Test
    void parsesFcListFamilies() {
        AppProperties props = new AppProperties();
        props.getOffice().setFcListPath("/usr/bin/fc-list");
        InstalledFontProvider provider =
            new FakeProvider(props, "Arial\nNoto Sans CJK KR,Noto Sans CJK KR Bold\n", Path.of("/usr/bin/fc-list"));
        assertThat(provider.installedFamilies()).contains("arial", "noto sans cjk kr");
        assertThat(provider.isAvailable()).isTrue();
    }

    @Test
    void returnsEmptyWhenBinaryMissing() {
        InstalledFontProvider provider = new FakeProvider(new AppProperties(), "", null);
        assertThat(provider.installedFamilies()).isEmpty();
        assertThat(provider.isAvailable()).isFalse();
    }

    private static class FakeProvider extends InstalledFontProvider {
        private final String output; private final Path executable;
        FakeProvider(AppProperties props, String output, Path executable) {
            super(props); this.output = output; this.executable = executable;
        }
        @Override protected Process start(ProcessBuilder b) { return new FakeProcess(output); }
        @Override protected boolean isExecutable(Path p) { return executable != null && executable.equals(p); }
        @Override protected String getPathEnv() { return null; }
    }

    private static class FakeProcess extends Process {
        private final byte[] out;
        FakeProcess(String o) { this.out = o.getBytes(StandardCharsets.UTF_8); }
        @Override public OutputStream getOutputStream() { return OutputStream.nullOutputStream(); }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(out); }
        @Override public InputStream getErrorStream() { return InputStream.nullInputStream(); }
        @Override public int waitFor() { return 0; }
        @Override public int exitValue() { return 0; }
        @Override public void destroy() {}
    }
}
