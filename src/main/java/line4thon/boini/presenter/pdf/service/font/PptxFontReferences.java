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

    private static final Pattern TYPEFACE = Pattern.compile("typeface=\"([^\"]*)\"");
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
                Matcher tf = TYPEFACE.matcher(xml);
                while (tf.find()) {
                    String v = tf.group(1);
                    if (v != null && !v.isBlank() && !v.startsWith("+")) addName(referenced, v);
                }
            }
        } catch (org.apache.poi.openxml4j.exceptions.InvalidFormatException e) {
            throw new IOException("PPTX 열기 실패", e);
        }
        return new Result(referenced, embedded);
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
