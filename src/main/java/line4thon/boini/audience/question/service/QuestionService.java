package line4thon.boini.audience.question.service;

import line4thon.boini.audience.question.dto.QuestionEvent;
import line4thon.boini.audience.question.dto.requeset.CreateQuestionRequest;
import line4thon.boini.audience.question.dto.response.CreateQuestionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionService {

  private final StringRedisTemplate redis;
  private final SimpMessagingTemplate broker;
  private static final long STREAM_MAXLEN = 10_000L;

  // 질문 생성 → 저장 → 실시간 브로드캐스트
  public CreateQuestionResponse create(String roomId, CreateQuestionRequest request) {
    // 5초 10회 제한 같은 간단 레이트리밋
    rateLimit(roomId, request.audienceId());

    String id = genId();
    long ts = (request.ts() != null) ? request.ts() : Instant.now().toEpochMilli();

    String hKey = "room:%s:question:%s".formatted(roomId, id);
    String zKey = "room:%s:page:%d:questions".formatted(roomId, request.slide());

    Map<String, String> h = new LinkedHashMap<>();
    h.put("id", id);
    h.put("roomId", roomId);
    h.put("slide", String.valueOf(request.slide()));
    h.put("audienceId", request.audienceId());
    h.put("content", request.content());
    h.put("ts", String.valueOf(ts));

    // 1) Redis 저장 (본문: HASH, 인덱스: ZSET(score=ts))
    redis.opsForHash().putAll(hKey, h);
    redis.opsForZSet().add(zKey, id, ts);
    log.debug("[REDIS] 질문 저장 완료 (roomId={}, questionId={}, slide={}, ts={})",
        roomId, id, request.slide(), ts);

    // 2) 브로드캐스트 (방의 모두에게)
    var response = new CreateQuestionResponse(id, roomId, request.slide(), request.audienceId(), request.content(), ts);
    String base = "/topic/p/" + roomId;
    broker.convertAndSend(base + "/public",    QuestionEvent.created(response));     // 청중/발표자 공통
    broker.convertAndSend(base + "/presenter", QuestionEvent.created(response));     // 발표자 전용(권한)

    log.info("[WS] 질문 브로드캐스트 완료 (roomId={}, audienceId={}, slide={}, content='{}')",
        roomId, request.audienceId(), request.slide(), request.content());

    // Redis Stream 발행 → 분석/알림 파이프라인 용

        Map<String, String> streamBody = Map.of(
            "type","question-created","roomId",roomId,"id",id,
            "slide", String.valueOf(request.slide()), "audienceId", request.audienceId(), "ts", String.valueOf(ts)
        );

    // 방 단위 스트림 권장: room별로 분리
    String streamKey = "stream:question:events:" + roomId;

    // XADD → 이후 TRIM 으로 최근 N건만 유지 (XAddOptions 없이 동작)  // [CHANGED]
    redis.opsForStream().add(StreamRecords.mapBacked(streamBody).withStreamKey(streamKey));
    redis.opsForStream().trim(streamKey, STREAM_MAXLEN, true);

    log.debug("[STREAM] 질문 이벤트 스트림 추가 (streamKey={}, type=question-created, ts={})", streamKey, ts);

    return response;
  }

  // 초기 로딩/무한스크롤용: 슬라이드별 시간 오름차순 일부
  public List<CreateQuestionResponse> list(String roomId, int slide, int limit, Long fromTs) {
    String zKey = "room:%s:page:%d:questions".formatted(roomId, slide);
    double min = (fromTs == null) ? Double.NEGATIVE_INFINITY : fromTs.doubleValue();
    double max = Double.POSITIVE_INFINITY;

    Set<ZSetOperations.TypedTuple<String>> ids =
        redis.opsForZSet().rangeByScoreWithScores(zKey, min, max, 0, limit);

    if (ids == null || ids.isEmpty()) {
      log.debug("[REDIS] 질문 없음 (roomId={}, slide={})", roomId, slide);
    return List.of();
  }

    var result = ids.stream()
        .filter(tt -> tt.getValue() != null && tt.getScore() != null)
        .map(tt -> toResponse(roomId, tt.getValue()))
        .filter(Objects::nonNull)
        .toList();

    log.info("[QUERY] 질문 조회 완료 (roomId={}, slide={}, count={})", roomId, slide, result.size());
    return result;
  }

  private CreateQuestionResponse toResponse(String roomId, String id) {
    Map<Object, Object> h = redis.opsForHash().entries("room:%s:question:%s".formatted(roomId, id));
    if (h.isEmpty()) {
      log.warn("[REDIS] 질문 상세 없음 (roomId={}, questionId={})", roomId, id);
      return null;
    }

    return new CreateQuestionResponse(
        String.valueOf(h.get("id")),
        String.valueOf(h.get("roomId")),
        Integer.parseInt(String.valueOf(h.get("slide"))),
        String.valueOf(h.get("audienceId")),
        String.valueOf(h.get("content")),
        Long.parseLong(String.valueOf(h.get("ts")))
    );
  }

  private String genId() {
    return Long.toString(Instant.now().toEpochMilli(), 36) + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  // 레이트리밋
    private void rateLimit(String roomId, String audienceId) {
        String key = "rate:%s:%s:q".formatted(roomId, audienceId);
        Long n = redis.opsForValue().increment(key);
        if (n != null && n == 1L) redis.expire(key, java.time.Duration.ofSeconds(5));
        if (n != null && n > 10) {
          log.warn("[RATE LIMIT] Too many requests (roomId={}, audienceId={}, count={})",
              roomId, audienceId, n);
            throw new RuntimeException("Too many requests");
        }
    }
}