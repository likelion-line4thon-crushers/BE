package line4thon.boini.presenter.pdf.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import line4thon.boini.global.common.exception.CustomException;
import line4thon.boini.global.config.AppProperties;
import line4thon.boini.presenter.image.service.SlideNoteService;
import line4thon.boini.presenter.pdf.exception.PdfErrorCode;
import line4thon.boini.presenter.pdf.service.font.FontUploadValidator;
import line4thon.boini.presenter.pdf.service.font.PresentationFontAnalysisService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * PdfChunkService 의 finalize/storeFonts 진입 가드(상태·uploadId 검증)를 실제로 구동하는 단위 테스트.
 * 협력자들은 Mockito 로 대체하고, Redis 상태값에 따른 예외 분기만 검증한다.
 */
class PdfChunkServiceFontBranchTest {

    private static final String VALID_ID = "11111111-1111-1111-1111-111111111111";

    private final ValueOperations<String, String> ops = mockOps();
    private final StringRedisTemplate redis = mockRedis(ops);
    private final PdfChunkService service = new PdfChunkService(
        new AppProperties(),
        redis,
        mock(PdfParseService.class),
        mock(OfficeConversionService.class),
        mock(SlideNotesExtractionService.class),
        mock(SlideNoteService.class),
        mock(S3Client.class),
        mock(PresentationFontAnalysisService.class),
        mock(FontUploadValidator.class),
        new ObjectMapper());

    @SuppressWarnings("unchecked")
    private static ValueOperations<String, String> mockOps() {
        return (ValueOperations<String, String>) mock(ValueOperations.class);
    }

    private static StringRedisTemplate mockRedis(ValueOperations<String, String> ops) {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenReturn(ops);
        return redis;
    }

    private void stateReturns(String state) {
        when(ops.get("pdf:upload:" + VALID_ID + ":state")).thenReturn(state);
    }

    private PdfErrorCode codeOf(Throwable t) {
        return (PdfErrorCode) ((CustomException) t).getErrorCode();
    }

    @Test
    void finalizeRejectsWhenStateNotAwaiting() {
        stateReturns("READY");
        assertThatThrownBy(() -> service.finalize(VALID_ID, false))
            .isInstanceOf(CustomException.class)
            .satisfies(t -> assertThat(codeOf(t)).isEqualTo(PdfErrorCode.SESSION_NOT_AWAITING_FONTS));
    }

    @Test
    void finalizeRejectsWhenStateMissing() {
        stateReturns(null);
        assertThatThrownBy(() -> service.finalize(VALID_ID, false))
            .isInstanceOf(CustomException.class)
            .satisfies(t -> assertThat(codeOf(t)).isEqualTo(PdfErrorCode.UPLOAD_SESSION_NOT_FOUND));
    }

    @Test
    void storeFontsRejectsWhenStateNotAwaiting() {
        stateReturns("READY");
        assertThatThrownBy(() -> service.storeFonts(VALID_ID, List.of()))
            .isInstanceOf(CustomException.class)
            .satisfies(t -> assertThat(codeOf(t)).isEqualTo(PdfErrorCode.SESSION_NOT_AWAITING_FONTS));
    }

    @Test
    void finalizeRejectsMalformedUploadId() {
        // 상태와 메타데이터를 모두 유효하게 스텁해, requireAwaitingFonts 나 meta 누락 체크가 아니라
        // requireValidUploadId 가 먼저 걸러내는지를 검증한다(가드 제거 시 이 테스트는 실패해야 한다).
        // meta 까지 채워두지 않으면 finalize() 뒷부분의 "meta 누락 → UPLOAD_SESSION_NOT_FOUND" 분기가
        // 우연히 같은 에러코드를 던져 가드가 빠져도 테스트가 통과하는 거짓 양성이 생긴다.
        when(ops.get("pdf:upload:not-a-uuid:state")).thenReturn("AWAITING_FONTS");
        when(ops.get("pdf:upload:not-a-uuid:meta:roomId")).thenReturn("r1");
        when(ops.get("pdf:upload:not-a-uuid:meta:deckId")).thenReturn("d1");
        when(ops.get("pdf:upload:not-a-uuid:meta:fileName")).thenReturn("deck.pdf");
        when(ops.get("pdf:upload:not-a-uuid:meta:fileType")).thenReturn("PDF");
        when(ops.get("pdf:upload:not-a-uuid:sourcePath")).thenReturn("/nonexistent/source.pdf");
        assertThatThrownBy(() -> service.finalize("not-a-uuid", false))
            .isInstanceOf(CustomException.class)
            .satisfies(t -> assertThat(codeOf(t)).isEqualTo(PdfErrorCode.UPLOAD_SESSION_NOT_FOUND));
    }

    @Test
    void storeFontsRejectsMalformedUploadId() {
        // 상태를 AWAITING_FONTS 로 스텁해, requireAwaitingFonts 가 아니라
        // requireValidUploadId 가 먼저 걸러내는지를 검증한다(가드 제거 시 이 테스트는 실패해야 한다).
        when(ops.get("pdf:upload:../etc:state")).thenReturn("AWAITING_FONTS");
        assertThatThrownBy(() -> service.storeFonts("../etc", List.of()))
            .isInstanceOf(CustomException.class)
            .satisfies(t -> assertThat(codeOf(t)).isEqualTo(PdfErrorCode.UPLOAD_SESSION_NOT_FOUND));
    }

    @Test
    void storeFontsRejectsWhenCumulativeCountExceedsCap(@TempDir Path tempDir) throws Exception {
        // 개수 상한은 이번 배치만이 아니라 이미 저장된 폰트까지 누적으로 검사해야 한다.
        // maxCount=1 로 두고 기존 폰트 1개가 이미 저장된 상태에서 1개를 더 올리면 초과되어야 한다.
        AppProperties localProps = new AppProperties();
        localProps.getPdf().setTempDir(tempDir.toString());
        localProps.getFonts().setMaxCount(1);

        ValueOperations<String, String> localOps = mockOps();
        StringRedisTemplate localRedis = mockRedis(localOps);
        when(localOps.get("pdf:upload:" + VALID_ID + ":state")).thenReturn("AWAITING_FONTS");

        PdfChunkService localService = new PdfChunkService(
            localProps,
            localRedis,
            mock(PdfParseService.class),
            mock(OfficeConversionService.class),
            mock(SlideNotesExtractionService.class),
            mock(SlideNoteService.class),
            mock(S3Client.class),
            mock(PresentationFontAnalysisService.class),
            mock(FontUploadValidator.class),
            new ObjectMapper());

        Path fontDir = tempDir.resolve(VALID_ID).resolve("fonts");
        Files.createDirectories(fontDir);
        Files.write(fontDir.resolve("existing.ttf"), new byte[] {0});

        MockMultipartFile incoming = new MockMultipartFile("fonts", "a.ttf", "font/ttf", new byte[] {1});

        assertThatThrownBy(() -> localService.storeFonts(VALID_ID, List.of(incoming)))
            .isInstanceOf(CustomException.class)
            .satisfies(t -> assertThat(codeOf(t)).isEqualTo(PdfErrorCode.TOO_MANY_FONTS));
    }
}
