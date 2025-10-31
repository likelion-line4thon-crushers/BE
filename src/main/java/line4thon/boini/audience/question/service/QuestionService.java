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
    String zRoom = "room:%s:questions".formatted(roomId);

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

    // XADD → 이후 TRIM 으로 최근 N건만 유지 (XAddOptions 없이 동작)
    redis.opsForStream().add(StreamRecords.mapBacked(streamBody).withStreamKey(streamKey));
    redis.opsForStream().trim(streamKey, STREAM_MAXLEN, true);
    redis.opsForZSet().add(zRoom, id, ts);

    log.debug("[STREAM] 질문 이벤트 스트림 추가 (streamKey={}, type=question-created, ts={})", streamKey, ts);

    return response;
  }

  // 전체 질문 조회용: 방 단위 시간 오름차순 전체 반환
  public List<CreateQuestionResponse> listRoom(String roomId, Long fromTs, Integer slide) {
    // room-wide ZSET: 질문 생성시 함께 적재되어 있어야 함 (member = questionId, score = ts)
    final String zRoom = "room:%s:questions".formatted(roomId);

    // fromTs 중복 방지: 배타 하한(> fromTs)로 처리
    // Double 점수 특성상 Math.nextUp 사용 (fromTs가 있을 때만)
    final double min = (fromTs == null) ? Double.NEGATIVE_INFINITY : Math.nextUp(fromTs.doubleValue());
    final double max = Double.POSITIVE_INFINITY;

    // slide 필터가 없으면 room-wide 인덱스로, 있으면 슬라이드 인덱스로 바로 당겨오는 게 효율적
    final String zKey = (slide == null)
        ? zRoom
        : "room:%s:page:%d:questions".formatted(roomId, slide);

    // limit 제거: 전체 범위 조회
    Set<ZSetOperations.TypedTuple<String>> tuples =
        redis.opsForZSet().rangeByScoreWithScores(zKey, min, max);

    if (tuples == null || tuples.isEmpty()) {
      log.debug("[REDIS] 질문 없음 (roomId={}, slide={})", roomId, slide);
      return List.of();
    }

    // HASH에서 본문 조립
    List<CreateQuestionResponse> result = new ArrayList<>(tuples.size());
    for (ZSetOperations.TypedTuple<String> t : tuples) {
      String qid = t.getValue();
      if (qid == null) continue;

      String hKey = "room:%s:question:%s".formatted(roomId, qid);
      Map<Object, Object> h = redis.opsForHash().entries(hKey);
      if (h == null || h.isEmpty()) continue;

      int s = Integer.parseInt((String) h.get("slide"));

      // slide 필터가 있는 경우, room-wide에서 가져왔을 때 추가 필터링
      if (slide != null && !zKey.endsWith(":questions")) {
        // 이미 슬라이드 인덱스를 썼으므로 추가 필터 불필요
      } else if (slide != null && s != slide) {
        continue;
      }

      long ts = Long.parseLong((String) h.get("ts"));
      result.add(new CreateQuestionResponse(
          (String) h.get("id"),
          (String) h.get("roomId"),
          s,
          (String) h.get("audienceId"),
          (String) h.get("content"),
          ts
      ));
    }

    // 오름차순(시간↑) 정렬 보장
    result.sort(Comparator.comparingLong(CreateQuestionResponse::ts));
    return result;
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
          log.warn("[RATE LIMIT] 요청이 너무 많습니다 (roomId={}, audienceId={}, count={})",
              roomId, audienceId, n);
            throw new RuntimeException("Too many requests");
        }
    }
}