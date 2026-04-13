package line4thon.boini.presenter.pdf.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import line4thon.boini.global.common.response.BaseResponse;
import line4thon.boini.presenter.pdf.dto.request.ChunkUploadRequest;
import line4thon.boini.presenter.pdf.dto.response.ChunkUploadResult;
import line4thon.boini.presenter.pdf.service.PdfChunkService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PDF 청크 단위 업로드 컨트롤러.
 *
 * 흐름: 프론트에서 PDF를 2MB 단위로 잘라 병렬로 전송
 *       → 이 컨트롤러가 청크를 받아 PdfChunkService 에 전달
 *       → 마지막 청크 수신 시 HTTP 201 + pdfId + streamUrl 반환
 *       → 프론트가 streamUrl(GET /api/pdf/{pdfId}/stream)을 SSE 구독
 *
 * 연결: PdfChunkService → PdfParseService(@Async) → PdfSseRegistry → PdfStreamController
 */
@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class ChunkUploadController {

    private final PdfChunkService pdfChunkService;

    /**
     * 청크 하나를 수신합니다.
     *
     * @ModelAttribute: multipart/form-data 필드들을 ChunkUploadRequest 에 바인딩합니다.
     *                  @RequestBody 가 아닌 @ModelAttribute 를 사용하는 이유는
     *                  chunk(바이너리)와 텍스트 필드를 같은 요청에서 받기 위해서입니다.
     *
     * 응답:
     *   200 OK      → 아직 수신 중 (status: IN_PROGRESS)
     *   201 Created → 모든 청크 수신 완료, PDF 조립 완료 (status: READY)
     */
    @PostMapping(value = "/chunk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
        summary = "PDF 청크 업로드",
        description = """
        PDF 바이너리를 청크 단위로 분할하여 업로드합니다.
        - 청크 최대 크기: 2MB
        - 수신 중: HTTP 200, status=IN_PROGRESS
        - 마지막 청크 수신 및 조립 완료: HTTP 201, status=READY + pdfId + streamUrl
        """
    )
    public ResponseEntity<BaseResponse<?>> uploadChunk(@Valid @ModelAttribute ChunkUploadRequest request) {
        ChunkUploadResult result = pdfChunkService.receiveChunk(request);

        // 조립 완료 여부에 따라 HTTP 상태 코드 분기
        if (result.complete()) {
            // 201: 모든 청크 수신 완료 → 프론트는 response.streamUrl 로 SSE 구독 시작
            return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.success(result.assembled()));
        }

        // 200: 아직 수신 중 → 프론트는 나머지 청크 계속 전송
        return ResponseEntity.ok(BaseResponse.success(result.progress()));
    }
}
