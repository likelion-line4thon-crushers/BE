package line4thon.boini.presenter.pdf.controller;

import io.swagger.v3.oas.annotations.Operation;
import line4thon.boini.presenter.pdf.service.PdfSseRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * PDF 페이지 스트리밍 컨트롤러.
 *
 * 흐름: 청크 업로드 완료(201) 후 프론트가 이 엔드포인트를 SSE 구독
 *       → PdfSseRegistry 에 SseEmitter 등록
 *       → PdfParseService 가 페이지 렌더링할 때마다 이 emitter 로 이벤트 push
 *
 * 연결: ChunkUploadController(201 응답) → [프론트 SSE 구독] → 이 컨트롤러
 *       이 컨트롤러 → PdfSseRegistry.register() → PdfParseService 가 emit()
 */
@RestController
@RequestMapping("/api/pdf")
@RequiredArgsConstructor
public class PdfStreamController {

    // PdfSseRegistry: pdfId별 SseEmitter를 관리하는 레지스트리
    private final PdfSseRegistry sseRegistry;

    /**
     * SSE 스트림을 구독합니다.
     *
     * produces = TEXT_EVENT_STREAM_VALUE: 응답 Content-Type을 text/event-stream 으로 설정.
     *   Spring이 SseEmitter를 반환값으로 받으면 커넥션을 닫지 않고 유지합니다.
     *
     * pdfId: 청크 업로드 201 응답의 pdfId 와 동일한 값입니다.
     *         PdfParseService 도 동일한 pdfId 로 이벤트를 emit 합니다.
     *
     * SSE 이벤트 종류 (PdfSseRegistry.emit 참고):
     *   - page     : 페이지 하나 렌더링 완료 시
     *   - complete : 전체 페이지 처리 완료 시
     *   - error    : 특정 페이지 렌더링 실패 시
     */
    @GetMapping(value = "/{pdfId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
        summary = "PDF 페이지 스트리밍 구독",
        description = """
        PDF 파싱 결과를 SSE(Server-Sent Events)로 수신합니다.
        - 청크 업로드 완료(HTTP 201) 직후 구독하세요.
        - 이벤트 종류: page, complete, error
        - page 이벤트의 canStartSession=true → 세션 시작 가능 신호 (첫 10페이지 완료 시)
        """
    )
    public SseEmitter stream(@PathVariable String pdfId) {
        // PdfParseService가 이미 일부 페이지를 처리했더라도
        // PdfSseRegistry의 버퍼 기능으로 놓친 이벤트를 즉시 재전송받습니다.
        return sseRegistry.register(pdfId);
    }
}
