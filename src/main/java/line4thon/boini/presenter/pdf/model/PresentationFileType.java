package line4thon.boini.presenter.pdf.model;

import java.util.Locale;
import java.util.Optional;

public enum PresentationFileType {
    PDF("pdf"),
    PPT("ppt"),
    PPTX("pptx");

    private final String extension;

    PresentationFileType(String extension) {
        this.extension = extension;
    }

    public String extension() {
        return extension;
    }

    public boolean requiresConversion() {
        return this == PPT || this == PPTX;
    }

    public static Optional<PresentationFileType> fromFileName(String fileName) {
        if (fileName == null) {
            return Optional.empty();
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return Optional.empty();
        }
        String ext = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        for (PresentationFileType type : values()) {
            if (type.extension.equals(ext)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
