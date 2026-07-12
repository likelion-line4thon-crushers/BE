package line4thon.boini.presenter.pdf.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.Font;
import java.awt.FontFormatException;
import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import line4thon.boini.global.common.exception.CustomException;
import line4thon.boini.global.config.AppProperties;
import line4thon.boini.presenter.image.service.SlideNoteService;
import line4thon.boini.presenter.pdf.dto.FontEntry;
import line4thon.boini.presenter.pdf.dto.SlideNoteDraft;
import line4thon.boini.presenter.pdf.dto.request.ChunkUploadRequest;
import line4thon.boini.presenter.pdf.dto.response.AssemblyCompleteResponse;
import line4thon.boini.presenter.pdf.dto.response.ChunkReceiveResponse;
import line4thon.boini.presenter.pdf.dto.response.ChunkUploadResult;
import line4thon.boini.presenter.pdf.dto.response.NeedsFontsResponse;
import line4thon.boini.presenter.pdf.exception.PdfErrorCode;
import line4thon.boini.presenter.pdf.model.FontStatus;
import line4thon.boini.presenter.pdf.model.PresentationFileType;
import line4thon.boini.presenter.pdf.service.font.FontNames;
import line4thon.boini.presenter.pdf.service.font.FontUploadValidator;
import line4thon.boini.presenter.pdf.service.font.PresentationFontAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

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
    private final OfficeConversionService officeConversionService;
    private final SlideNotesExtractionService slideNotesExtractionService;
    private final SlideNoteService slideNoteService;
    private final S3Client s3Client;
    private final PresentationFontAnalysisService fontAnalysisService;
    private final FontUploadValidator fontUploadValidator;
    private final ObjectMapper objectMapper;

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
        PresentationFileType.fromFileName(request.getFileName())
            .orElseThrow(() -> new CustomException(PdfErrorCode.UNSUPPORTED_PRESENTATION_FILE));
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
        PresentationFileType.fromFileName(request.getFileName())
            .ifPresent(type -> setIfAbsent(metaKey(uploadId, "fileType"), type.name()));
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
        PresentationFileType fileType = PresentationFileType.fromFileName(request.getFileName())
            .orElseThrow(() -> new CustomException(PdfErrorCode.UNSUPPORTED_PRESENTATION_FILE));
        Path assembledPath = assembleChunks(uploadId, request.getTotalChunks(), fileType);
        long assemblyMs = Duration.between(assemblyStart, Instant.now()).toMillis();
        log.info("[⏱ 성능] 청크 조립 완료: {}ms | 파일크기: {}", assemblyMs,
            formatBytes(getFileSize(assembledPath)));

        String pdfId = generatePdfId();

        // Redis 에서 메타 조회 (saveMetaIfAbsent 에서 저장한 값)
        String roomId = redis.opsForValue().get(metaKey(uploadId, "roomId"));
        String deckId = redis.opsForValue().get(metaKey(uploadId, "deckId"));

        // Redis TTL 만료로 메타가 사라진 경우 (업로드가 1시간을 넘긴 경우)
        if (roomId == null || deckId == null) {
            log.error("[조립] Redis 메타데이터 누락 (세션 만료 가능성): uploadId={}", uploadId);
            throw new CustomException(PdfErrorCode.ASSEMBLY_FAILED);
        }

        // PPTX 에 한해 폰트 분석을 수행하여 누락 폰트가 있는지 확인합니다.
        // 누락 폰트가 있으면 변환을 보류하고 발표자에게 폰트 업로드를 요청합니다(AWAITING_FONTS).
        List<FontEntry> report = fileType == PresentationFileType.PPTX
            ? fontAnalysisService.analyze(assembledPath)
            : List.of();
        boolean hasMissing = report.stream().anyMatch(e -> e.status() == FontStatus.MISSING);

        if (hasMissing) {
            List<String> missing = report.stream()
                .filter(e -> e.status() == FontStatus.MISSING).map(FontEntry::name).toList();
            try {
                redis.opsForValue().set(stateKey(uploadId), "AWAITING_FONTS", UPLOAD_SESSION_TTL);
                redis.opsForValue().set(missingFontsKey(uploadId),
                    objectMapper.writeValueAsString(missing), UPLOAD_SESSION_TTL);
                redis.opsForValue().set(sourcePathKey(uploadId), assembledPath.toString(), UPLOAD_SESSION_TTL);
            } catch (JsonProcessingException e) {
                throw new CustomException(PdfErrorCode.ASSEMBLY_FAILED);
            }
            // meta 와 조립된 파일은 finalize 에서 재사용하므로 cleanupRedisKeys 를 호출하지 않습니다.
            log.info("[조립] 폰트 대기 상태 전환: uploadId={}, missing={}", uploadId, missing);
            return ChunkUploadResult.needsFonts(NeedsFontsResponse.of(uploadId, report));
        }

        AssemblyCompleteResponse response = convertSeedAndParse(
            uploadId, pdfId, roomId, deckId, assembledPath, fileType, request.getFileName(), null);
        cleanupRedisKeys(uploadId); // Redis 에서 임시 메타 삭제 (임시 파일은 PdfParseService finally 에서 삭제)
        log.info("[조립] 완료: uploadId={}, pdfId={}, totalPages={}", uploadId, pdfId, response.getTotalPages());
        return ChunkUploadResult.assembled(response);
    }

    /**
     * 변환 → Redis 시딩 → 비동기 파싱 트리거까지의 공통 단계.
     * 마지막 청크의 빠른 경로(fontDir=null)와 finalize(fontDir=발표자 업로드 폰트) 양쪽에서 재사용됩니다.
     */
    private AssemblyCompleteResponse convertSeedAndParse(
        String uploadId, String pdfId, String roomId, String deckId,
        Path assembledPath, PresentationFileType fileType, String fileName, Path fontDir) {

        PreparedPresentation prepared = preparePresentationForParsing(assembledPath, fileType, fontDir);
        Path pdfPath = prepared.pdfPath();

        // PDFBox 로 페이지 수만 빠르게 확인 (렌더링 없음, 수 ms 수준)
        // 201 응답에 totalPages 를 포함해야 해서 동기적으로 처리합니다.
        Instant pageCountStart = Instant.now();
        int totalPages = countPdfPages(pdfPath, uploadId);
        log.info("[⏱ 성능] 페이지 수 확인: {}ms | totalPages={}",
            Duration.between(pageCountStart, Instant.now()).toMillis(), totalPages);

        slideNoteService.replaceNotes(roomId, deckId, prepared.notes());
        uploadDownloadablePdf(roomId, deckId, pdfPath, fileName);

        // 방 생성 시 totalPage 는 placeholder(1)로 저장된다(프론트가 업로드 전 createRoom(1) 호출).
        // 실제 페이지 수가 확정된 지금 갱신하지 않으면, audience join 응답의 totalPages 가 1로 남아
        // 청중이 1페이지만 보고 발표자 페이지 이동도 따라가지 못한다.
        redis.opsForValue().set("room:" + roomId + ":totalPage", String.valueOf(totalPages));
        log.info("[조립] room totalPage 갱신: roomId={}, totalPages={}", roomId, totalPages);

        // 방 생성 시 placeholder(totalPages=1)로 slide/revisit 세트가 슬라이드 1만 초기화됐다.
        // 실제 페이지 수가 확정된 지금 2..N 도 동일하게 시딩하지 않으면, PageService.getSlideAudienceCounts
        // 의 (setSize - 1) 보정이 sentinel 없는 슬라이드에서 음수/누락 집계를 내어 청중 분포 인디케이터가 깨진다.
        for (int i = 1; i <= totalPages; i++) {
            redis.opsForSet().add("room:" + roomId + ":slide:" + i, "_init_");      // set-add 는 멱등
            redis.opsForValue().setIfAbsent("room:" + roomId + ":revisit:" + i, "0"); // 기존 값/카운트 보존
        }

        // @Async: 즉시 반환됩니다. 실제 렌더링은 pdfParseExecutor 스레드풀에서 진행됩니다.
        // 렌더링 결과는 PdfSseRegistry 를 통해 프론트로 스트리밍됩니다.
        pdfParseService.parseAndStream(pdfId, roomId, deckId, pdfPath, totalPages);

        return AssemblyCompleteResponse.builder()
            .status("READY")
            .uploadId(uploadId)
            .pdfId(pdfId)
            .fileName(fileName)
            .totalPages(totalPages)
            .streamUrl("/api/pdf/" + pdfId + "/stream") // 프론트가 SSE 구독할 URL
            .build();
    }

    private void uploadDownloadablePdf(String roomId, String deckId, Path pdfPath, String sourceFileName) {
        String key = downloadablePdfKey(roomId, deckId);
        try {
            s3Client.putObject(
                PutObjectRequest.builder()
                    .bucket(props.getS3().getBucket())
                    .key(key)
                    .contentType("application/pdf")
                    .build(),
                RequestBody.fromFile(pdfPath)
            );
            redis.opsForValue().set(pdfDownloadS3Key(roomId), key);
            redis.opsForValue().set(pdfDownloadFileNameKey(roomId), toPdfFileName(sourceFileName));
            log.info("[PDF] 다운로드용 PDF 업로드 완료: roomId={}, key={}", roomId, key);
        } catch (RuntimeException e) {
            log.error("[PDF] 다운로드용 PDF 업로드 실패: roomId={}, key={}", roomId, key, e);
            throw new CustomException(PdfErrorCode.ASSEMBLY_FAILED);
        }
    }

    private String downloadablePdfKey(String roomId, String deckId) {
        String root = normalizePrefix(props.getS3().getRootPrefix());
        String path = "%s/%s/source.pdf".formatted(roomId, deckId);
        return root.isEmpty() ? path : root + "/" + path;
    }

    private String toPdfFileName(String sourceFileName) {
        if (sourceFileName == null || sourceFileName.isBlank()) {
            return "boini-slides.pdf";
        }

        String fileName = sourceFileName.replace("\\", "/");
        int slash = fileName.lastIndexOf('/');
        if (slash >= 0) {
            fileName = fileName.substring(slash + 1);
        }

        int dot = fileName.lastIndexOf('.');
        String title = dot > 0 ? fileName.substring(0, dot) : fileName;
        title = title.replaceAll("[\\p{Cntrl}\\\\/:*?\"<>|]+", "_").trim();

        return title.isBlank() ? "boini-slides.pdf" : title + ".pdf";
    }

    private String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return "";
        }
        return prefix.replaceAll("^/+", "").replaceAll("/+$", "");
    }

    private String pdfDownloadS3Key(String roomId) {
        return "room:" + roomId + ":pdfDownload:s3Key";
    }

    private String pdfDownloadFileNameKey(String roomId) {
        return "room:" + roomId + ":pdfDownload:fileName";
    }

    /**
     * 저장된 청크 파일들을 순서대로 이어붙여 하나의 PDF 파일로 조립합니다.
     * 저장 경로: {uploadDir}/source.{ext}
     * chunk_00000 → chunk_00001 → ... 순서대로 이어붙입니다.
     */
    private Path assembleChunks(String uploadId, int totalChunks, PresentationFileType fileType) {
        Path uploadDir = resolveUploadDir(uploadId);
        Path assembledPath = uploadDir.resolve("source." + fileType.extension());
        try (FileOutputStream out = new FileOutputStream(assembledPath.toFile())) {
            for (int i = 0; i < totalChunks; i++) {
                Files.copy(resolveChunkPath(uploadId, i), out);
            }
            log.info("[조립] 파일 생성 완료: path={}", assembledPath);
            return assembledPath;
        } catch (IOException e) {
            log.error("[조립] 청크 병합 실패: uploadId={}", uploadId, e);
            throw new CustomException(PdfErrorCode.ASSEMBLY_FAILED);
        }
    }

    private PreparedPresentation preparePresentationForParsing(
        Path assembledPath, PresentationFileType fileType, Path fontDir) {
        if (!fileType.requiresConversion()) {
            return new PreparedPresentation(assembledPath, List.of());
        }

        List<SlideNoteDraft> notes = List.of();
        try {
            notes = slideNotesExtractionService.extract(assembledPath, fileType);
        } catch (Exception e) {
            log.warn("[PPT] 발표자 노트 추출 실패, 슬라이드 변환은 계속 진행: file={}", assembledPath, e);
        }

        return new PreparedPresentation(officeConversionService.convertToPdf(assembledPath, fontDir), notes);
    }

    private record PreparedPresentation(Path pdfPath, List<SlideNoteDraft> notes) {
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
        redis.delete(metaKey(uploadId, "fileType"));
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

    private String stateKey(String uploadId) { return "pdf:upload:" + uploadId + ":state"; }
    private String missingFontsKey(String uploadId) { return "pdf:upload:" + uploadId + ":missingFonts"; }
    private String sourcePathKey(String uploadId) { return "pdf:upload:" + uploadId + ":sourcePath"; }

    /**
     * AWAITING_FONTS 상태의 업로드 세션에 발표자가 폰트 파일을 업로드합니다.
     * 저장 후 원본 파일을 재분석하여 갱신된 폰트 리포트를 반환합니다.
     */
    public List<FontEntry> storeFonts(String uploadId, List<MultipartFile> fonts) {
        requireValidUploadId(uploadId);
        requireAwaitingFonts(uploadId);
        Path fontDir = resolveUploadDir(uploadId).resolve("fonts");
        // 업로드된 폰트의 패밀리명(정규화)을 모아 재분석 시 "사용 가능"으로 반영한다.
        Set<String> uploadedNormalized = new HashSet<>();
        try {
            Files.createDirectories(fontDir);
            // 개수 상한은 이번 배치만이 아니라 이미 저장된 파일까지 누적으로 검사한다.
            long existingCount = countRegularFiles(fontDir);
            if (existingCount + fonts.size() > props.getFonts().getMaxCount()) {
                throw new CustomException(PdfErrorCode.TOO_MANY_FONTS);
            }
            long total = directorySize(fontDir);
            for (MultipartFile font : fonts) {
                byte[] bytes = font.getBytes();
                fontUploadValidator.validate(font.getOriginalFilename(), bytes);
                total += bytes.length;
                if (total > props.getFonts().getMaxTotalBytes()) {
                    throw new CustomException(PdfErrorCode.FONT_UPLOAD_TOO_LARGE_TOTAL);
                }
                Files.write(fontDir.resolve(safeFontName(font.getOriginalFilename())), bytes);
                String family = Font.createFont(Font.TRUETYPE_FONT, new ByteArrayInputStream(bytes)).getFamily();
                uploadedNormalized.add(FontNames.normalize(family));
            }
        } catch (IOException e) {
            log.error("[Font] 폰트 저장 실패: uploadId={}", uploadId, e);
            throw new CustomException(PdfErrorCode.CHUNK_SAVE_FAILED);
        } catch (FontFormatException e) {
            // validate() 가 이미 폰트 유효성을 검사하므로 정상 경로에선 도달하지 않는다.
            log.error("[Font] 폰트 패밀리 파싱 실패: uploadId={}", uploadId, e);
            throw new CustomException(PdfErrorCode.INVALID_FONT_FILE);
        }
        String sourcePath = redis.opsForValue().get(sourcePathKey(uploadId));
        return sourcePath == null ? List.of()
            : fontAnalysisService.analyze(Paths.get(sourcePath), uploadedNormalized);
    }

    /**
     * AWAITING_FONTS 상태의 업로드 세션을 종료합니다.
     * proceedWithoutFonts=true 이면 발표자가 업로드한 폰트 없이(폰트 대체 없이) 변환을 진행합니다.
     */
    public AssemblyCompleteResponse finalize(String uploadId, boolean proceedWithoutFonts) {
        requireValidUploadId(uploadId);
        requireAwaitingFonts(uploadId);
        String roomId = redis.opsForValue().get(metaKey(uploadId, "roomId"));
        String deckId = redis.opsForValue().get(metaKey(uploadId, "deckId"));
        String fileName = redis.opsForValue().get(metaKey(uploadId, "fileName"));
        String fileTypeName = redis.opsForValue().get(metaKey(uploadId, "fileType"));
        String sourcePath = redis.opsForValue().get(sourcePathKey(uploadId));
        if (roomId == null || deckId == null || fileName == null || fileTypeName == null || sourcePath == null) {
            throw new CustomException(PdfErrorCode.UPLOAD_SESSION_NOT_FOUND);
        }
        PresentationFileType fileType = PresentationFileType.valueOf(fileTypeName);
        Path fontDir = proceedWithoutFonts ? null : resolveExistingFontDir(uploadId);
        String pdfId = generatePdfId();

        AssemblyCompleteResponse response = convertSeedAndParse(
            uploadId, pdfId, roomId, deckId, Paths.get(sourcePath), fileType, fileName, fontDir);

        redis.delete(stateKey(uploadId));
        redis.delete(missingFontsKey(uploadId));
        redis.delete(sourcePathKey(uploadId));
        cleanupRedisKeys(uploadId);
        log.info("[조립] finalize 완료: uploadId={}, pdfId={}, withFonts={}", uploadId, pdfId, fontDir != null);
        return response;
    }

    private void requireAwaitingFonts(String uploadId) {
        String state = redis.opsForValue().get(stateKey(uploadId));
        if (state == null) throw new CustomException(PdfErrorCode.UPLOAD_SESSION_NOT_FOUND);
        if (!"AWAITING_FONTS".equals(state)) throw new CustomException(PdfErrorCode.SESSION_NOT_AWAITING_FONTS);
    }

    // uploadId 는 파일시스템 경로와 fonts.conf XML 에 그대로 사용되므로, 위조된 값을 조기에 거부한다.
    // 프론트는 uuidv4() 로 생성하므로 UUID 형식 검증은 안전하다.
    private static final Pattern UPLOAD_ID_PATTERN = Pattern.compile(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private void requireValidUploadId(String uploadId) {
        if (uploadId == null || !UPLOAD_ID_PATTERN.matcher(uploadId).matches()) {
            throw new CustomException(PdfErrorCode.UPLOAD_SESSION_NOT_FOUND);
        }
    }

    private long countRegularFiles(Path dir) throws IOException {
        if (!Files.exists(dir)) return 0;
        try (var s = Files.list(dir)) {
            return s.filter(Files::isRegularFile).count();
        }
    }

    private Path resolveExistingFontDir(String uploadId) {
        Path fontDir = resolveUploadDir(uploadId).resolve("fonts");
        return Files.isDirectory(fontDir) ? fontDir : null;
    }

    private long directorySize(Path dir) throws IOException {
        if (!Files.exists(dir)) return 0;
        try (var s = Files.walk(dir)) {
            return s.filter(Files::isRegularFile).mapToLong(p -> {
                try { return Files.size(p); } catch (IOException e) { return 0; }
            }).sum();
        }
    }

    private String safeFontName(String original) {
        String base = original == null ? "font" : original.replace("\\", "/");
        int slash = base.lastIndexOf('/');
        if (slash >= 0) base = base.substring(slash + 1);
        base = base.replaceAll("[\\p{Cntrl}:*?\"<>|]+", "_").trim();
        return base.isBlank() ? "font-" + UUID.randomUUID() : base;
    }
}
