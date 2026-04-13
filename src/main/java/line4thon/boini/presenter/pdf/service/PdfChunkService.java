package line4thon.boini.presenter.pdf.service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import line4thon.boini.global.common.exception.CustomException;
import line4thon.boini.global.config.AppProperties;
import line4thon.boini.presenter.pdf.dto.request.ChunkUploadRequest;
import line4thon.boini.presenter.pdf.dto.response.AssemblyCompleteResponse;
import line4thon.boini.presenter.pdf.dto.response.ChunkReceiveResponse;
import line4thon.boini.presenter.pdf.dto.response.ChunkUploadResult;
import line4thon.boini.presenter.pdf.exception.PdfErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * PDF 청크 수신, 임시 저장, 조립을 담당하는 서비스.
 *
 * 흐름:
 *   1. 청크 수신 → 임시 파일로 저장 (로컬 temp 디렉토리)
 *   2. Redis INCR 으로 수신 카운트 원자적 증가
 *   3. 카운트 == totalChunks 인 스레드가 조립을 담당
 *   4. 조립 완료 → 페이지 수 확인 → PdfParseService(@Async) 트리거
 *
 * 연결:
 *   - ChunkUploadController → receiveChunk() 호출
 *   - PdfParseService.parseAndStream() → 조립 완료 후 비동기 파싱 시작
 *   - Redis: 청크 수신 카운팅 및 roomId/deckId 등 메타데이터 임시 저장
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PdfChunkService {

    // Redis 키 TTL: 업로드 세션 만료 시간 (1시간)
    // 이 시간 안에 모든 청크가 도착하지 않으면 세션 만료로 ASSEMBLY_FAILED 발생
    private static final Duration UPLOAD_SESSION_TTL = Duration.ofHours(1);

    // Redis 키 패턴
    private static final String COUNT_KEY = "pdf:upload:%s:count"; // 수신 청크 카운터
    private static final String META_KEY  = "pdf:upload:%s:meta:%s"; // roomId, deckId 등 메타

    private final AppProperties props;
    private final StringRedisTemplate redis;
    private final PdfParseService pdfParseService; // 조립 완료 후 비동기 파싱 트리거

    /**
     * 청크 하나를 수신하고 처리합니다. (ChunkUploadController 에서 호출)
     *
     * @return ChunkUploadResult.inProgress → HTTP 200 (아직 수신 중)
     *         ChunkUploadResult.assembled  → HTTP 201 (조립 완료)
     */
    public ChunkUploadResult receiveChunk(ChunkUploadRequest request) {
        validateChunk(request);
        saveChunkFile(request);
        saveMetaIfAbsent(request); // 첫 번째 청크에서만 실질적으로 저장됨 (setIfAbsent)

        long receivedCount = incrementReceivedCount(request.getUploadId());

        if (receivedCount == request.getTotalChunks()) {
            // 이 스레드가 마지막 청크를 받은 스레드 → 조립 담당
            return assembleAndTriggerParsing(request);
        }

        return ChunkUploadResult.inProgress(ChunkReceiveResponse.builder()
            .uploadId(request.getUploadId())
            .chunkIndex(request.getChunkIndex())
            .receivedChunks(receivedCount)
            .totalChunks(request.getTotalChunks())
            .chunkSize(request.getChunk().getSize())
            .status("IN_PROGRESS")
            .build());
    }

    /**
     * 청크 크기 및 인덱스 유효성 검사.
     * 최대 크기는 application.properties → app.pdf.max-chunk-size-bytes (기본값 2MB)
     */
    private void validateChunk(ChunkUploadRequest request) {
        if (request.getChunk().getSize() > props.getPdf().getMaxChunkSizeBytes()) {
            throw new CustomException(PdfErrorCode.CHUNK_TOO_LARGE);
        }
        if (request.getChunkIndex() < 0 || request.getChunkIndex() >= request.getTotalChunks()) {
            throw new CustomException(PdfErrorCode.INVALID_CHUNK_INDEX);
        }
    }

    /**
     * 청크 바이너리를 로컬 임시 파일로 저장합니다.
     * 저장 경로: {app.pdf.temp-dir}/{uploadId}/chunk_{00000~}
     * 조립 시 인덱스 순서대로 이 파일들을 이어붙입니다.
     */
    private void saveChunkFile(ChunkUploadRequest request) {
        Path chunkPath = resolveChunkPath(request.getUploadId(), request.getChunkIndex());
        try {
            Files.createDirectories(chunkPath.getParent());
            try (InputStream in = request.getChunk().getInputStream();
                 FileOutputStream out = new FileOutputStream(chunkPath.toFile())) {
                in.transferTo(out);
            }
            log.debug("[청크] 저장 완료: path={}", chunkPath);
        } catch (IOException e) {
            log.error("[청크] 저장 실패: uploadId={}, index={}", request.getUploadId(), request.getChunkIndex(), e);
            throw new CustomException(PdfErrorCode.CHUNK_SAVE_FAILED);
        }
    }

    /**
     * 업로드 세션 메타데이터를 Redis 에 저장합니다.
     * setIfAbsent: 최초 청크가 도착했을 때만 저장되고, 이후 청크는 덮어쓰지 않습니다.
     * 이 값은 조립 완료 시 PdfParseService 에 roomId/deckId 를 전달하는 데 사용됩니다.
     */
    private void saveMetaIfAbsent(ChunkUploadRequest request) {
        String uploadId = request.getUploadId();
        setIfAbsent(metaKey(uploadId, "roomId"), request.getRoomId());
        setIfAbsent(metaKey(uploadId, "deckId"), request.getDeckId());
        setIfAbsent(metaKey(uploadId, "fileName"), request.getFileName());
        setIfAbsent(metaKey(uploadId, "totalChunks"), String.valueOf(request.getTotalChunks()));
    }

    private void setIfAbsent(String key, String value) {
        redis.opsForValue().setIfAbsent(key, value, UPLOAD_SESSION_TTL);
    }

    /**
     * 수신 카운트를 Redis INCR 로 원자적으로 증가시킵니다.
     * INCR 은 원자적 연산이므로 병렬 요청 환경에서도 정확히 한 스레드만
     * receivedCount == totalChunks 조건을 만족하게 됩니다.
     */
    private long incrementReceivedCount(String uploadId) {
        String key = COUNT_KEY.formatted(uploadId);
        Long count = redis.opsForValue().increment(key);
        redis.expire(key, UPLOAD_SESSION_TTL);
        return count == null ? 0L : count;
    }

    /**
     * 모든 청크를 조립하고 PdfParseService 비동기 파싱을 트리거합니다.
     * 조립 완료 응답(201)에 pdfId 와 streamUrl 을 포함하여 반환합니다.
     *
     * 주의: cleanupRedisKeys 는 pdfParseService.parseAndStream 호출 이후에 실행됩니다.
     *       parseAndStream 은 @Async 이므로 즉시 반환되며, Redis 에서 roomId/deckId 를
     *       읽은 후에 정리합니다.
     */
    private ChunkUploadResult assembleAndTriggerParsing(ChunkUploadRequest request) {
        String uploadId = request.getUploadId();
        log.info("[조립] 모든 청크 수신 완료, 조립 시작: uploadId={}", uploadId);

        // ── [성능 측정] 조립 시간 ──
        Instant assemblyStart = Instant.now();
        Path assembledPath = assembleChunks(uploadId, request.getTotalChunks());
        long assemblyMs = Duration.between(assemblyStart, Instant.now()).toMillis();
        log.info("[⏱ 성능] 청크 조립 완료: {}ms | 파일크기: {}", assemblyMs,
            formatBytes(getFileSize(assembledPath)));

        // PDFBox 로 페이지 수만 빠르게 확인 (렌더링 없음, 수 ms 수준)
        // 201 응답에 totalPages 를 포함해야 해서 동기적으로 처리합니다.
        Instant pageCountStart = Instant.now();
        int totalPages = countPdfPages(assembledPath, uploadId);
        log.info("[⏱ 성능] 페이지 수 확인: {}ms | totalPages={}",
            Duration.between(pageCountStart, Instant.now()).toMillis(), totalPages);

        String pdfId = generatePdfId();

        // Redis 에서 메타 조회 (saveMetaIfAbsent 에서 저장한 값)
        String roomId = redis.opsForValue().get(metaKey(uploadId, "roomId"));
        String deckId = redis.opsForValue().get(metaKey(uploadId, "deckId"));

        // Redis TTL 만료로 메타가 사라진 경우 (업로드가 1시간을 넘긴 경우)
        if (roomId == null || deckId == null) {
            log.error("[조립] Redis 메타데이터 누락 (세션 만료 가능성): uploadId={}", uploadId);
            throw new CustomException(PdfErrorCode.ASSEMBLY_FAILED);
        }

        // @Async: 즉시 반환됩니다. 실제 렌더링은 pdfParseExecutor 스레드풀에서 진행됩니다.
        // 렌더링 결과는 PdfSseRegistry 를 통해 프론트로 스트리밍됩니다.
        pdfParseService.parseAndStream(pdfId, roomId, deckId, assembledPath, totalPages);

        cleanupRedisKeys(uploadId); // Redis 에서 임시 메타 삭제 (임시 파일은 PdfParseService finally 에서 삭제)

        log.info("[조립] 완료: uploadId={}, pdfId={}, totalPages={}", uploadId, pdfId, totalPages);

        return ChunkUploadResult.assembled(AssemblyCompleteResponse.builder()
            .status("READY")
            .uploadId(uploadId)
            .pdfId(pdfId)
            .fileName(request.getFileName())
            .totalPages(totalPages)
            .streamUrl("/api/pdf/" + pdfId + "/stream") // 프론트가 SSE 구독할 URL
            .build());
    }

    /**
     * 저장된 청크 파일들을 순서대로 이어붙여 하나의 PDF 파일로 조립합니다.
     * 저장 경로: {uploadDir}/assembled.pdf
     * chunk_00000 → chunk_00001 → ... 순서대로 이어붙입니다.
     */
    private Path assembleChunks(String uploadId, int totalChunks) {
        Path uploadDir = resolveUploadDir(uploadId);
        Path assembledPath = uploadDir.resolve("assembled.pdf");
        try (FileOutputStream out = new FileOutputStream(assembledPath.toFile())) {
            for (int i = 0; i < totalChunks; i++) {
                Files.copy(resolveChunkPath(uploadId, i), out);
            }
            log.info("[조립] PDF 파일 생성 완료: path={}", assembledPath);
            return assembledPath;
        } catch (IOException e) {
            log.error("[조립] 청크 병합 실패: uploadId={}", uploadId, e);
            throw new CustomException(PdfErrorCode.ASSEMBLY_FAILED);
        }
    }

    /**
     * PDFBox 로 PDF 파일을 열어 총 페이지 수를 반환합니다.
     * 렌더링 없이 메타데이터만 읽으므로 빠릅니다.
     * 연결: 반환값이 201 응답의 totalPages 와 PdfParseService 의 루프 범위에 사용됩니다.
     */
    private int countPdfPages(Path pdfPath, String uploadId) {
        try (PDDocument doc = Loader.loadPDF(pdfPath.toFile())) {
            int pages = doc.getNumberOfPages();
            log.info("[PDF] 총 페이지 수: uploadId={}, pages={}", uploadId, pages);
            return pages;
        } catch (IOException e) {
            log.error("[PDF] 페이지 수 확인 실패: uploadId={}", uploadId, e);
            throw new CustomException(PdfErrorCode.PDF_PARSE_FAILED);
        }
    }

    // 조립 완료 후 Redis 임시 키 정리
    private void cleanupRedisKeys(String uploadId) {
        redis.delete(COUNT_KEY.formatted(uploadId));
        redis.delete(metaKey(uploadId, "roomId"));
        redis.delete(metaKey(uploadId, "deckId"));
        redis.delete(metaKey(uploadId, "fileName"));
        redis.delete(metaKey(uploadId, "totalChunks"));
    }

    // pdfId: "pdf-" + UUID 앞 12자리 (예: pdf-a1b2c3d4e5f6)
    private String generatePdfId() {
        return "pdf-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private long getFileSize(Path path) {
        try { return Files.size(path); } catch (IOException e) { return -1; }
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.2f MB", bytes / (1024.0 * 1024));
    }

    // {app.pdf.temp-dir}/{uploadId}
    private Path resolveUploadDir(String uploadId) {
        return Paths.get(props.getPdf().getTempDir(), uploadId);
    }

    // {app.pdf.temp-dir}/{uploadId}/chunk_{index:05d}
    private Path resolveChunkPath(String uploadId, int chunkIndex) {
        return resolveUploadDir(uploadId).resolve("chunk_%05d".formatted(chunkIndex));
    }

    private String metaKey(String uploadId, String field) {
        return META_KEY.formatted(uploadId, field);
    }
}
