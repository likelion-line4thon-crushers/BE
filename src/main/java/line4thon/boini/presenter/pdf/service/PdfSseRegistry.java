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
 * SSE 연결과 이벤트 히스토리를 관리하는 레지스트리.
 *
 * 존재 이유 (히스토리가 필요한 이유):
 *   청크 업로드 201 반환 → PdfParseService가 @Async로 즉시 파싱 시작
 *   → 프론트가 SSE 구독하기 전에 이미 몇 페이지가 처리될 수 있음
 *   → SSE 미연결/재연결 상태에서도 모든 이벤트를 히스토리에서 재전송
 *
 * 리프레시 대응:
 *   이벤트를 히스토리에 항상 누적하고, register() 시 전체 히스토리를 재전송합니다.
 *   히스토리는 complete(pdfId) 호출 시에만 삭제됩니다.
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

    // pdfId → SseEmitter: 현재 SSE 연결이 맺어진 emitter
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    // pdfId → 이벤트 히스토리: 파싱 중 발생한 모든 이벤트를 보관 (재연결 시 재전송용)
    // complete(pdfId) 호출 시에만 삭제됩니다.
    // ConcurrentLinkedQueue: 여러 스레드(pdfParseExecutor)가 동시에 add 할 수 있어 thread-safe 자료구조 사용
    private final Map<String, ConcurrentLinkedQueue<BufferedEvent>> history = new ConcurrentHashMap<>();

    // 히스토리에 저장할 이벤트 단위 (SSE event name + data)
    private record BufferedEvent(String name, Object data) {}

    /**
     * SSE 연결을 등록합니다. (PdfStreamController 에서 호출)
     * 등록 즉시 히스토리에 쌓인 모든 이벤트를 재전송합니다 (리프레시 대응).
     */
    public SseEmitter register(String pdfId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        // 클라이언트 연결 종료 / 타임아웃 / 오류 발생 시 emitter만 제거 (히스토리는 보존)
        emitter.onTimeout(() -> removeEmitter(pdfId, emitter));
        emitter.onError(e -> removeEmitter(pdfId, emitter));
        emitter.onCompletion(() -> removeEmitter(pdfId, emitter));

        emitters.put(pdfId, emitter);

        // emitter 등록 후 히스토리에 쌓인 모든 이벤트 재전송 (히스토리는 삭제하지 않음)
        replayHistory(pdfId, emitter);

        log.info("[SSE] 연결 등록: pdfId={}", pdfId);
        return emitter;
    }

    /**
     * 이벤트를 전송합니다. (PdfParseService 에서 호출)
     * 항상 히스토리에 누적하고, SSE 연결 중이면 즉시 live 전송합니다.
     *
     * @param eventName SSE 이벤트 이름 ("page" | "complete" | "error")
     * @param data      JSON 직렬화될 이벤트 데이터 (PageEventData, CompleteEventData, ErrorEventData)
     */
    public void emit(String pdfId, String eventName, Object data) {
        // 항상 히스토리에 누적 (재연결 시 재전송을 위해)
        history.computeIfAbsent(pdfId, k -> new ConcurrentLinkedQueue<>())
            .add(new BufferedEvent(eventName, data));

        SseEmitter emitter = emitters.get(pdfId);
        if (emitter != null) {
            // 연결된 emitter에 즉시 live 전송
            sendEvent(pdfId, emitter, eventName, data);
        } else {
            log.debug("[SSE] 이벤트 히스토리 적재 (SSE 미연결): pdfId={}, event={}", pdfId, eventName);
        }
    }

    /**
     * SSE 스트림을 종료하고 리소스를 정리합니다. (PdfParseService 에서 호출)
     * complete 또는 error 이벤트 전송 후 호출됩니다.
     * emitter와 히스토리를 모두 제거합니다 (파싱 종료 후에는 재연결이 불필요).
     */
    public void complete(String pdfId) {
        SseEmitter emitter = emitters.get(pdfId);
        if (emitter != null) {
            emitter.complete();
        }
        // 파싱 종료: emitter와 히스토리 모두 정리
        emitters.remove(pdfId);
        history.remove(pdfId);
        log.info("[SSE] 스트림 종료: pdfId={}", pdfId);
    }

    /**
     * 특정 emitter만 제거합니다. 히스토리는 보존합니다.
     * ConcurrentHashMap.remove(key, value): stale 콜백이 더 새로운 emitter를 지우지 못하도록 방어합니다.
     * onTimeout / onError / onCompletion 콜백에서 호출됩니다.
     */
    private void removeEmitter(String pdfId, SseEmitter emitter) {
        emitters.remove(pdfId, emitter);
        log.debug("[SSE] emitter 제거 (히스토리 보존): pdfId={}", pdfId);
    }

    /**
     * SseEmitter 로 이벤트를 실제 전송합니다.
     * 전송 실패(클라이언트 연결 끊김) 시 emitter만 제거하고 히스토리는 보존합니다.
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
        } catch (IOException | IllegalStateException e) {
            log.warn("[SSE] 이벤트 전송 실패 (클라이언트 연결 끊김): pdfId={}, event={}", pdfId, eventName);
            removeEmitter(pdfId, emitter);
        }
    }

    /**
     * 히스토리에 쌓인 모든 이벤트를 순서대로 재전송합니다.
     * register() 에서 emitter 등록 직후 호출됩니다.
     * 히스토리는 삭제하지 않으므로 다음 재연결 시에도 재전송 가능합니다.
     */
    private void replayHistory(String pdfId, SseEmitter emitter) {
        ConcurrentLinkedQueue<BufferedEvent> buffered = history.get(pdfId);
        if (buffered == null || buffered.isEmpty()) return;

        log.info("[SSE] 히스토리 재전송: pdfId={}, count={}", pdfId, buffered.size());
        for (BufferedEvent event : buffered) {
            sendEvent(pdfId, emitter, event.name(), event.data());
        }
    }
}
