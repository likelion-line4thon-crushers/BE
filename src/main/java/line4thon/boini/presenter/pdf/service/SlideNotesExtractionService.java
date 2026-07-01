package line4thon.boini.presenter.pdf.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import line4thon.boini.presenter.pdf.dto.SlideNoteDraft;
import line4thon.boini.presenter.pdf.model.PresentationFileType;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hslf.usermodel.HSLFNotes;
import org.apache.poi.hslf.usermodel.HSLFShape;
import org.apache.poi.hslf.usermodel.HSLFSlide;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hslf.usermodel.HSLFTextShape;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFNotes;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SlideNotesExtractionService {

    public List<SlideNoteDraft> extract(Path sourceFile, PresentationFileType type) throws IOException {
        return switch (type) {
            case PPTX -> extractPptx(sourceFile);
            case PPT -> extractPpt(sourceFile);
            case PDF -> List.of();
        };
    }

    private List<SlideNoteDraft> extractPptx(Path sourceFile) throws IOException {
        try (InputStream input = Files.newInputStream(sourceFile);
             XMLSlideShow slideShow = new XMLSlideShow(input)) {
            List<SlideNoteDraft> notes = new ArrayList<>();
            List<XSLFSlide> slides = slideShow.getSlides();
            for (int i = 0; i < slides.size(); i++) {
                XSLFNotes slideNotes = slides.get(i).getNotes();
                String text = extractPptxNotesText(slideNotes);
                if (hasText(text)) {
                    notes.add(new SlideNoteDraft(i + 1, text));
                }
            }
            log.info("[PPTX] 발표자 노트 추출 완료: file={}, notes={}", sourceFile.getFileName(), notes.size());
            return notes;
        }
    }

    private String extractPptxNotesText(XSLFNotes notes) {
        if (notes == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (XSLFShape shape : notes.getShapes()) {
            if (shape instanceof XSLFTextShape textShape) {
                String text = textShape.getText();
                if (hasText(text)) {
                    parts.add(text.trim());
                }
            }
        }
        return String.join("\n", parts).trim();
    }

    private List<SlideNoteDraft> extractPpt(Path sourceFile) throws IOException {
        try (InputStream input = Files.newInputStream(sourceFile);
             HSLFSlideShow slideShow = new HSLFSlideShow(input)) {
            List<SlideNoteDraft> notes = new ArrayList<>();
            List<HSLFSlide> slides = slideShow.getSlides();
            for (int i = 0; i < slides.size(); i++) {
                HSLFNotes slideNotes = slides.get(i).getNotes();
                String text = extractPptNotesText(slideNotes);
                if (hasText(text)) {
                    notes.add(new SlideNoteDraft(i + 1, text));
                }
            }
            log.info("[PPT] 발표자 노트 추출 완료: file={}, notes={}", sourceFile.getFileName(), notes.size());
            return notes;
        }
    }

    private String extractPptNotesText(HSLFNotes notes) {
        if (notes == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (HSLFShape shape : notes.getShapes()) {
            if (shape instanceof HSLFTextShape textShape) {
                String text = textShape.getText();
                if (hasText(text)) {
                    parts.add(text.trim());
                }
            }
        }
        return String.join("\n", parts).trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
