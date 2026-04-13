package line4thon.boini.presenter.pdf.service;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 연결과 이벤트 버퍼를 관리하는 레지스트리.
 *
 * 존재 이유 (버퍼링이 필요한 이유):
 *   청크 업로드 201 반환 → PdfParseService가 @Async로 즉시 파싱 시작
 *   → 프론트가 SSE 구독하기 전에 이미 몇 페이지가 처리될 수 있음
 *   → SSE 미연결 상태의 이벤트를 버퍼에 적재했다가 구독 시 즉시 재전송
 *
 * 연결:
 *   - PdfStreamController.stream() → register() 호출 (프론트 SSE 구독 시)
 *   - PdfParseService.processPage() → emit() 호출 (페이지 렌더링 완료 시)
 *   - PdfParseService.parseAndStream() → complete() 호출 (전체 완료 시)
 */
@Component
@Slf4j
public class PdfSseRegistry {

    private static final long SSE_TIMEOUT_MS = 10 * 60 * 1000L; // 10분

    // pdfId → SseEmitter: 현재 SSE 연결이 맺어진 emitter 목록
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    // pdfId → 이벤트 큐: SSE 연결 전에 들어온 이벤트를 임시 보관
    // ConcurrentLinkedQueue: 여러 스레드(pdfParseExecutor)가 동시에 add 할 수 있어 thread-safe 자료구조 사용
    private final Map<String, ConcurrentLinkedQueue<BufferedEvent>> eventBuffer = new ConcurrentHashMap<>();

    // 버퍼에 저장할 이벤트 단위 (SSE event name + data)
    private record BufferedEvent(String name, Object data) {}

    /**
     * SSE 연결을 등록합니다. (PdfStreamController 에서 호출)
     * 이미 버퍼에 쌓인 이벤트가 있으면 등록 즉시 재전송합니다.
     */
    public SseEmitter register(String pdfId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        // 클라이언트 연결 종료 / 타임아웃 / 오류 발생 시 리소스 정리
        emitter.onTimeout(() -> cleanup(pdfId));
        emitter.onError(e -> cleanup(pdfId));
        emitter.onCompletion(() -> cleanup(pdfId));

        emitters.put(pdfId, emitter);

        // emitter 등록 후 버퍼에 쌓인 이벤트 즉시 재전송
        flushBuffer(pdfId, emitter);

        log.info("[SSE] 연결 등록: pdfId={}", pdfId);
        return emitter;
    }

    /**
     * 이벤트를 전송합니다. (PdfParseService 에서 호출)
     * SSE 미연결 상태이면 버퍼에 적재하고, 연결 시 flushBuffer 로 자동 재전송됩니다.
     *
     * @param eventName SSE 이벤트 이름 ("page" | "complete" | "error")
     * @param data      JSON 직렬화될 이벤트 데이터 (PageEventData, CompleteEventData, ErrorEventData)
     */
    public void emit(String pdfId, String eventName, Object data) {
        SseEmitter emitter = emitters.get(pdfId);
        if (emitter == null) {
            // 프론트가 아직 SSE를 구독하지 않은 상태 → 버퍼에 보관
            buffer(pdfId, eventName, data);
            return;
        }
        sendEvent(pdfId, emitter, eventName, data);
    }

    /**
     * SSE 스트림을 종료하고 리소스를 정리합니다. (PdfParseService 에서 호출)
     * complete 이벤트 전송 후 호출됩니다.
     */
    public void complete(String pdfId) {
        SseEmitter emitter = emitters.get(pdfId);
        if (emitter != null) {
            emitter.complete();
        }
        cleanup(pdfId);
        log.info("[SSE] 스트림 종료: pdfId={}", pdfId);
    }

    /**
     * SseEmitter 로 이벤트를 실제 전송합니다.
     * 전송 실패(클라이언트 연결 끊김) 시 조용히 cleanup 합니다.
     *
     * MediaType.APPLICATION_JSON: data 필드를 JSON 으로 직렬화해서 전송
     */
    private void sendEvent(String pdfId, SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(
                SseEmitter.event()
                    .name(eventName)
                    .data(data, MediaType.APPLICATION_JSON)
            );
        } catch (IOException e) {
            log.warn("[SSE] 이벤트 전송 실패 (클라이언트 연결 끊김): pdfId={}, event={}", pdfId, eventName);
            cleanup(pdfId);
        }
    }

    /**
     * SSE 미연결 상태의 이벤트를 버퍼에 적재합니다.
     * computeIfAbsent: pdfId 에 대한 큐가 없으면 새로 생성 (thread-safe)
     */
    private void buffer(String pdfId, String eventName, Object data) {
        eventBuffer.computeIfAbsent(pdfId, k -> new ConcurrentLinkedQueue<>())
            .add(new BufferedEvent(eventName, data));
        log.debug("[SSE] 이벤트 버퍼링 (SSE 미연결): pdfId={}, event={}", pdfId, eventName);
    }

    /**
     * 버퍼에 쌓인 이벤트를 순서대로 재전송합니다.
     * register() 에서 emitter 등록 직후 호출됩니다.
     * eventBuffer.remove: 재전송 후 버퍼를 비워 중복 전송을 방지합니다.
     */
    private void flushBuffer(String pdfId, SseEmitter emitter) {
        ConcurrentLinkedQueue<BufferedEvent> buffered = eventBuffer.remove(pdfId);
        if (buffered == null || buffered.isEmpty()) return;

        log.info("[SSE] 버퍼 이벤트 재전송: pdfId={}, count={}", pdfId, buffered.size());
        for (BufferedEvent event : buffered) {
            sendEvent(pdfId, emitter, event.name(), event.data());
        }
    }

    // emitter 와 버퍼를 맵에서 제거합니다.
    private void cleanup(String pdfId) {
        emitters.remove(pdfId);
        eventBuffer.remove(pdfId);
    }
}
