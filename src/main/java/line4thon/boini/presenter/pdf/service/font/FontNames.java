package line4thon.boini.presenter.pdf.service.font;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class FontNames {
    private static final List<String> STYLE_WORDS =
        List.of("regular", "bold", "italic", "oblique", "light", "medium", "semibold", "thin", "black");

    private FontNames() {}

    public static String normalize(String raw) {
        if (raw == null) return "";
        String collapsed = raw.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        String[] parts = collapsed.split(" ");
        int end = parts.length;
        while (end > 1 && STYLE_WORDS.contains(parts[end - 1])) end--;
        return String.join(" ", Arrays.copyOfRange(parts, 0, end));
    }
}
