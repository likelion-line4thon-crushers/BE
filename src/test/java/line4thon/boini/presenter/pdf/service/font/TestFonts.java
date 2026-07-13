package line4thon.boini.presenter.pdf.service.font;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class TestFonts {
    private TestFonts() {}
    static byte[] anyTrueTypeFontBytes() {
        List<Path> candidates = List.of(
            Path.of("/System/Library/Fonts/Supplemental/Arial.ttf"),
            Path.of("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"),
            Path.of("/Library/Fonts/Arial.ttf"));
        for (Path p : candidates) {
            try { if (Files.isRegularFile(p)) return Files.readAllBytes(p); } catch (Exception ignored) {}
        }
        return null;
    }
}
