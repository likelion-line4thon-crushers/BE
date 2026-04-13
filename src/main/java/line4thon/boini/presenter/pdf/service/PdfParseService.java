package line4thon.boini.presenter.pdf.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import line4thon.boini.global.config.AppProperties;
import line4thon.boini.presenter.image.service.SlideS3Helper;
import line4thon.boini.presenter.pdf.dto.event.CompleteEventData;
import line4thon.boini.presenter.pdf.dto.event.ErrorEventData;
import line4thon.boini.presenter.pdf.dto.event.PageEventData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * 조립된 PDF 파일을 페이지별로 렌더링하고 S3에 업로드하며 SSE로 스트리밍하는 서비스.
 *
 * 흐름:
 *   1. PdfChunkService 에서 @Async 로 호출됨 → pdfParseExecutor 스레드풀에서 실행
 *   2. PDFBox 로 PDF 열기 → 각 페이지를 BufferedImage 로 렌더링
 *   3. Thumbnailator 로 WebP 변환 → S3 업로드 (원본 + 썸네일)
 *   4. 페이지 완료마다 PdfSseRegistry.emit("page", ...) 으로 프론트에 푸시
 *   5. 모든 페이지 완료 → emit("complete", ...) → sseRegistry.complete()
 *
 * 연결:
 *   - PdfChunkService.assembleAndTriggerParsing() → parseAndStream() 호출
 *   - SlideS3Helper: S3 키 생성 및 URL 발급 (DeckAssetService 와 공유)
 *   - PdfSseRegistry: 페이지 완료 이벤트 전달
 *   - AsyncConfig("pdfParseExecutor"): 이 서비스의 스레드풀 설정
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PdfParseService {

    private static final int RENDER_DPI = 150;               // PDF 렌더링 해상도 (높을수록 선명, 느림)
    private static final String IMAGE_FORMAT = "webp";        // S3 저장 포맷
    private static final String IMAGE_CONTENT_TYPE = "image/webp";
    private static final float THUMBNAIL_QUALITY = 0.8f;     // 썸네일 압축률 (0.0~1.0)
    private static final int THUMBNAIL_SIZE = 320;            // 썸네일 최대 너비/높이 (px)

    private final S3Client s3;
    private final AppProperties props;
    private final SlideS3Helper slideS3Helper; // S3 키 생성 및 URL 발급 (DeckAssetService 와 공유)
    private final PdfSseRegistry sseRegistry;  // 렌더링 완료 이벤트를 프론트로 전달

    /**
     * PDF 파일을 페이지별로 파싱하고 SSE 로 스트리밍합니다.
     *
     * @Async("pdfParseExecutor"): 호출 즉시 반환됩니다.
     *   실제 렌더링은 AsyncConfig 의 pdfParseExecutor 스레드풀에서 진행됩니다.
     *   @Async 가 동작하려면 반드시 다른 빈(PdfChunkService)에서 호출해야 합니다.
     *
     * @param pdfFile    PdfChunkService 가 조립한 assembled.pdf 경로
     * @param totalPages PdfChunkService 가 PDFBox 로 미리 확인한 총 페이지 수
     */
    @Async("pdfParseExecutor")
    public void parseAndStream(String pdfId, String roomId, String deckId, Path pdfFile, int totalPages) {
        log.info("[PDF] 비동기 파싱 시작: pdfId={}, totalPages={}", pdfId, totalPages);

        // ── [성능 측정] 전체 파싱 시간 ──
        Instant parseStart = Instant.now();

        try (PDDocument document = Loader.loadPDF(pdfFile.toFile())) {
            PDFRenderer renderer = new PDFRenderer(document);
            // application.properties → app.pdf.can-start-session-after-pages (기본값 10)
            int canStartSessionAfter = props.getPdf().getCanStartSessionAfterPages();

            for (int i = 0; i < totalPages; i++) {
                processPage(pdfId, roomId, deckId, renderer, i, totalPages, canStartSessionAfter);
            }

            long totalMs = Duration.between(parseStart, Instant.now()).toMillis();
            log.info("[⏱ 성능] 전체 파싱 완료: {}ms | 평균 {}ms/페이지 | totalPages={}",
                totalMs, totalPages > 0 ? totalMs / totalPages : 0, totalPages);

            // 모든 페이지 처리 완료 → SSE "complete" 이벤트 전송 후 연결 종료
            sseRegistry.emit(pdfId, "complete", CompleteEventData.builder()
                .pdfId(pdfId)
                .totalPages(totalPages)
                .status("DONE")
                .build());
            sseRegistry.complete(pdfId);
            log.info("[PDF] 파싱 완료: pdfId={}", pdfId);

        } catch (IOException e) {
            // PDF 파일 자체를 열 수 없는 경우 (손상된 PDF, 잘못된 조립 등)
            log.error("[PDF] PDF 열기 실패: pdfId={}", pdfId, e);
            sseRegistry.emit(pdfId, "error", ErrorEventData.builder()
                .pdfId(pdfId)
                .pageIndex(-1)
                .message("PDF 파일 열기 실패")
                .code("PDF_LOAD_FAILED")
                .build());
            sseRegistry.complete(pdfId);
        } finally {
            // 성공/실패 무관하게 temp 디렉토리 정리
            // (청크 파일들 + assembled.pdf 포함)
            deleteUploadDir(pdfFile.getParent());
        }
    }

    /**
     * 페이지 하나를 렌더링하고 S3에 업로드한 뒤 SSE "page" 이벤트를 발행합니다.
     * 단일 페이지 처리 실패 시 SSE "error" 이벤트를 발행하고 다음 페이지로 넘어갑니다.
     */
    private void processPage(
        String pdfId, String roomId, String deckId,
        PDFRenderer renderer, int pageIndex, int totalPages, int canStartSessionAfter
    ) {
        try {
            // ── [성능 측정] 페이지별 시간 ──
            Instant pageStart = Instant.now();

            // PDFBox 렌더링: pageIndex 는 0-based, pageNumber(S3 키) 는 1-based
            BufferedImage image = renderer.renderImageWithDPI(pageIndex, RENDER_DPI);
            long renderMs = Duration.between(pageStart, Instant.now()).toMillis();

            int pageNumber = pageIndex + 1;

            // 원본 WebP → S3 업로드 → URL 반환 (SlideS3Helper.buildUrl 로 CloudFront 분기)
            Instant uploadStart = Instant.now();
            String imageUrl = uploadOriginal(roomId, deckId, pageNumber, image);

            // 썸네일 (320x320 WebP) → S3 업로드 (DeckAssetController GET /meta 에서 사용)
            uploadThumbnail(roomId, deckId, pageNumber, image);
            long uploadMs = Duration.between(uploadStart, Instant.now()).toMillis();

            long pageMs = Duration.between(pageStart, Instant.now()).toMillis();
            log.info("[⏱ 성능] 페이지 {} 완료: 총 {}ms (렌더링 {}ms + S3업로드 {}ms) | {}x{}",
                pageIndex, pageMs, renderMs, uploadMs, image.getWidth(), image.getHeight());

            sseRegistry.emit(pdfId, "page", PageEventData.builder()
                .pdfId(pdfId)
                .pageIndex(pageIndex)
                .totalPages(totalPages)
                .imageUrl(imageUrl)
                .format(IMAGE_FORMAT)
                .width(image.getWidth())
                .height(image.getHeight())
                // canStartSession: 설정된 페이지 수(기본 10장)가 모두 완료됐을 때 true
                // 총 페이지가 10장 미만이면 마지막 페이지에서 true
                .canStartSession(isCanStartSession(pageIndex, totalPages, canStartSessionAfter))
                .build());

        } catch (Exception e) {
            log.error("[PDF] 페이지 처리 실패: pdfId={}, pageIndex={}", pdfId, pageIndex, e);
            // 단일 페이지 실패 → error 이벤트 발행 후 다음 페이지로 계속 진행
            sseRegistry.emit(pdfId, "error", ErrorEventData.builder()
                .pdfId(pdfId)
                .pageIndex(pageIndex)
                .message("페이지 렌더링 실패")
                .code("RENDER_FAILED")
                .build());
        }
    }

    /**
     * canStartSession 조건:
     *   - 정상: pageIndex 가 (canStartSessionAfter - 1) 에 도달했을 때 (ex. 10번째 페이지 완료)
     *   - 총 페이지가 threshold 미만: 마지막 페이지에서 true
     */
    private boolean isCanStartSession(int pageIndex, int totalPages, int canStartSessionAfter) {
        int threshold = Math.min(canStartSessionAfter, totalPages);
        return pageIndex == threshold - 1;
    }

    /**
     * 원본 이미지를 WebP 로 변환하고 S3에 업로드합니다.
     * 키 형식: {root}/{roomId}/{deckId}/pages/{0001}.webp
     * URL: SlideS3Helper.buildUrl(key, false) → CloudFront 설정 시 공개 URL, 아니면 presigned URL
     */
    private String uploadOriginal(String roomId, String deckId, int pageNumber, BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Thumbnails.of(image)
            .scale(1.0) // 원본 크기 유지
            .outputFormat(IMAGE_FORMAT)
            .outputQuality(THUMBNAIL_QUALITY)
            .toOutputStream(out);

        String key = slideS3Helper.buildKey(roomId, deckId, pageNumber, false, IMAGE_FORMAT);
        s3.putObject(
            PutObjectRequest.builder()
                .bucket(props.getS3().getBucket())
                .key(key)
                .contentType(IMAGE_CONTENT_TYPE)
                .build(),
            RequestBody.fromBytes(out.toByteArray())
        );
        log.info("[S3] 원본 업로드: {}", key);
        return slideS3Helper.buildUrl(key, false);
    }

    /**
     * 썸네일 이미지를 WebP 로 변환하고 S3에 업로드합니다.
     * 키 형식: {root}/{roomId}/{deckId}/thumbs/{0001}.webp
     * 연결: DeckAssetController GET /{roomId}/{deckId}/meta 에서 이 키의 URL을 반환합니다.
     */
    private void uploadThumbnail(String roomId, String deckId, int pageNumber, BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Thumbnails.of(image)
            .size(THUMBNAIL_SIZE, THUMBNAIL_SIZE)
            .outputFormat(IMAGE_FORMAT)
            .outputQuality(THUMBNAIL_QUALITY)
            .toOutputStream(out);

        String key = slideS3Helper.buildKey(roomId, deckId, pageNumber, true, IMAGE_FORMAT);
        s3.putObject(
            PutObjectRequest.builder()
                .bucket(props.getS3().getBucket())
                .key(key)
                .contentType(IMAGE_CONTENT_TYPE)
                .build(),
            RequestBody.fromBytes(out.toByteArray())
        );
        log.info("[S3] 썸네일 업로드: {}", key);
    }

    /**
     * 임시 업로드 디렉토리를 재귀적으로 삭제합니다.
     * parseAndStream 의 finally 블록에서 호출되어 청크 파일과 assembled.pdf 를 정리합니다.
     */
    private void deleteUploadDir(Path dir) {
        if (dir == null || !Files.exists(dir)) return;
        try {
            Files.walk(dir)
                .sorted(Comparator.reverseOrder()) // 하위 파일 먼저 삭제 후 디렉토리 삭제
                .forEach(path -> {
                    try { Files.delete(path); } catch (IOException ignored) {}
                });
            log.info("[PDF] 임시 파일 정리 완료: {}", dir);
        } catch (IOException e) {
            log.warn("[PDF] 임시 파일 정리 실패: {}", dir);
        }
    }
}
