package line4thon.boini.presenter.pdf.service.font;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import line4thon.boini.presenter.pdf.dto.FontEntry;
import line4thon.boini.presenter.pdf.model.FontStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PresentationFontAnalysisService {
    private final InstalledFontProvider installedFontProvider;

    public List<FontEntry> analyze(Path pptx) {
        try {
            PptxFontReferences.Result refs = PptxFontReferences.read(pptx);
            Set<String> installed = installedFontProvider.installedFamilies();
            Set<String> embeddedNorm = new HashSet<>();
            for (String n : refs.embedded()) embeddedNorm.add(FontNames.normalize(n));

            List<FontEntry> report = new ArrayList<>();
            for (String name : refs.referenced()) {
                String norm = FontNames.normalize(name);
                boolean embedded = embeddedNorm.contains(norm);
                boolean isInstalled = installed.contains(norm);
                FontStatus status = (embedded || isInstalled) ? FontStatus.AVAILABLE : FontStatus.MISSING;
                report.add(new FontEntry(name, status, embedded, isInstalled));
            }
            return report;
        } catch (Exception e) {
            log.warn("[Font] 분석 실패, 폰트 프롬프트 없이 변환을 진행합니다: file={}", pptx.getFileName(), e);
            return List.of();
        }
    }
}
