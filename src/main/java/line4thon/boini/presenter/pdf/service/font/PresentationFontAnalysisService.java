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
        return analyze(pptx, Set.of());
    }

    /**
     * @param extraAvailableNormalized 설치 폰트 외에 추가로 사용 가능하다고 볼 폰트(정규화된 패밀리명).
     *                                 발표자가 업로드한 폰트를 리포트에 반영하기 위해 사용된다.
     */
    public List<FontEntry> analyze(Path pptx, Set<String> extraAvailableNormalized) {
        // fc-list 를 실행할 수 없으면 설치 폰트를 알 수 없으므로 누락 판단을 건너뛴다.
        // (빈 리스트 → 상위에서 "누락 없음"으로 처리되어 변환 빠른 경로로 진행)
        if (!installedFontProvider.isAvailable()) {
            return List.of();
        }
        try {
            PptxFontReferences.Result refs = PptxFontReferences.read(pptx);
            Set<String> installed = installedFontProvider.installedFamilies();
            Set<String> embeddedNorm = new HashSet<>();
            for (String n : refs.embedded()) embeddedNorm.add(FontNames.normalize(n));

            List<FontEntry> report = new ArrayList<>();
            for (String name : refs.referenced()) {
                String norm = FontNames.normalize(name);
                boolean embedded = embeddedNorm.contains(norm);
                boolean isInstalled = installed.contains(norm) || extraAvailableNormalized.contains(norm);
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
