package line4thon.boini.presenter.pdf.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import line4thon.boini.global.common.exception.CustomException;
import line4thon.boini.global.config.AppProperties;
import line4thon.boini.presenter.image.service.SlideNoteService;
import line4thon.boini.presenter.pdf.exception.PdfErrorCode;
import line4thon.boini.presenter.pdf.service.font.FontUploadValidator;
import line4thon.boini.presenter.pdf.service.font.PresentationFontAnalysisService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
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
        assertThatThrownBy(() -> service.finalize("not-a-uuid", false))
            .isInstanceOf(CustomException.class)
            .satisfies(t -> assertThat(codeOf(t)).isEqualTo(PdfErrorCode.UPLOAD_SESSION_NOT_FOUND));
    }

    @Test
    void storeFontsRejectsMalformedUploadId() {
        assertThatThrownBy(() -> service.storeFonts("../etc", List.of()))
            .isInstanceOf(CustomException.class)
            .satisfies(t -> assertThat(codeOf(t)).isEqualTo(PdfErrorCode.UPLOAD_SESSION_NOT_FOUND));
    }
}
