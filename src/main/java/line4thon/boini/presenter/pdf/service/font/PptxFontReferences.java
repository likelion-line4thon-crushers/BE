package line4thon.boini.presenter.pdf.service.font;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.openxml4j.opc.PackagePart;

public final class PptxFontReferences {
    public record Result(Set<String> referenced, Set<String> embedded) {}

    // Only <a:latin>, <a:ea>, <a:cs> elements are real font references (explicit run fonts and the
    // theme's primary major/minor fonts). The theme's per-script fallback table uses
    // <a:font script="..."> and must NOT be counted — otherwise a normal default theme reports its
    // ~40 script fallback fonts (맑은 고딕, 游ゴシック, 等线, Segoe UI, ...) as "used".
    private static final Pattern RUN_FONT =
        Pattern.compile("<a:(?:latin|ea|cs)\\b[^>]*\\btypeface=\"([^\"]*)\"");
    private static final Pattern EMBEDDED_FONT =
        Pattern.compile("<p:embeddedFont>.*?typeface=\"([^\"]*)\"", Pattern.DOTALL);

    private PptxFontReferences() {}

    public static Result read(Path pptx) throws IOException {
        Set<String> referenced = new LinkedHashSet<>();
        Set<String> embedded = new LinkedHashSet<>();
        try (OPCPackage pkg = OPCPackage.open(pptx.toFile(), PackageAccess.READ)) {
            for (PackagePart part : pkg.getParts()) {
                String name = part.getPartName().getName();
                boolean relevant = name.startsWith("/ppt/theme/") || name.startsWith("/ppt/slides/")
                    || name.startsWith("/ppt/slideLayouts/") || name.startsWith("/ppt/slideMasters/")
                    || name.equals("/ppt/presentation.xml");
                if (!relevant) continue;
                String xml = readPart(part);
                if (name.equals("/ppt/presentation.xml")) {
                    Matcher em = EMBEDDED_FONT.matcher(xml);
                    while (em.find()) addName(embedded, em.group(1));
                }
                addReferencedFonts(xml, referenced);
            }
        } catch (org.apache.poi.openxml4j.exceptions.InvalidFormatException e) {
            throw new IOException("PPTX 열기 실패", e);
        }
        return new Result(referenced, embedded);
    }

    /**
     * Collects concrete font names referenced by text runs and theme primary fonts from one part's
     * XML. Matches only {@code <a:latin/ea/cs typeface="...">}, so the theme's per-script fallback
     * table ({@code <a:font script="...">}) is excluded. Theme-token references (+mj-lt, +mn-lt, ...)
     * are skipped; their concrete targets appear as {@code <a:latin/ea/cs>} in the theme part and are
     * captured there.
     */
    static void addReferencedFonts(String xml, Set<String> out) {
        Matcher m = RUN_FONT.matcher(xml);
        while (m.find()) {
            String v = m.group(1);
            if (v != null && !v.isBlank() && !v.startsWith("+")) addName(out, v);
        }
    }

    private static void addName(Set<String> set, String value) {
        String t = value == null ? "" : value.trim();
        if (!t.isEmpty()) set.add(t);
    }

    private static String readPart(PackagePart part) throws IOException {
        try (InputStream in = part.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
