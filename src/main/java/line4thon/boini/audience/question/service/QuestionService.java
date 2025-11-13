package line4thon.boini.audience.question.service;

import line4thon.boini.audience.question.dto.QuestionEvent;
import line4thon.boini.audience.question.dto.requeset.CreateQuestionRequest;
import line4thon.boini.audience.question.dto.response.CreateQuestionResponse;
import line4thon.boini.audience.question.exception.AudienceQuestionErrorCode;
import line4thon.boini.global.common.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;
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
  private final RedisTemplate<String, String> redisTemplate;

  public CreateQuestionResponse create(String roomId, CreateQuestionRequest request) {

    rateLimit(roomId, request.audienceId());

    String id = genId();
    long ts = (request.ts() != null) ? request.ts() : Instant.now().toEpochMilli();

    String hKey = "room:%s:question:%s".formatted(roomId, id);
    String zKey = "room:%s:page:%d:questions".formatted(roomId, request.slide());
    String zRoom = "room:%s:questions".formatted(roomId);
    String key1 = "room:"+roomId+":questionCount";

    redisTemplate.opsForValue().increment(key1);

    Map<String, String> h = new LinkedHashMap<>();
    h.put("id", id);
    h.put("roomId", roomId);
    h.put("slide", String.valueOf(request.slide()));
    h.put("audienceId", request.audienceId());
    h.put("content", request.content());
    h.put("ts", String.valueOf(ts));

    try {
      redis.opsForHash().putAll(hKey, h);
      redis.opsForZSet().add(zKey, id, ts);
      redis.opsForZSet().add(zRoom, id, ts);
      log.debug("[REDIS] 질문 저장 완료 (roomId={}, questionId={}, slide={}, ts={})",
          roomId, id, request.slide(), ts);
    } catch (Exception e) {
      log.error("[REDIS] 질문 저장 실패 (roomId={}, audienceId={})", roomId, request.audienceId(), e);
      throw new CustomException(AudienceQuestionErrorCode.REDIS_ERROR);
    }

    var response = new CreateQuestionResponse(id, roomId, request.slide(), request.audienceId(), request.content(), ts);
    String base = "/topic/p/" + roomId;
    broker.convertAndSend(base + "/public",    QuestionEvent.created(response));     // 청중/발표자 공통
    broker.convertAndSend(base + "/presenter", QuestionEvent.created(response));     // 발표자 전용(권한)

    log.info("[WS] 질문 실시간 전송 완료 (roomId={}, audienceId={}, slide={}, content='{}')",
        roomId, request.audienceId(), request.slide(), request.content());

    try {
      Map<String, String> streamBody = Map.of(
          "type", "question-created",
          "roomId", roomId,
          "id", id,
          "slide", String.valueOf(request.slide()),
          "audienceId", request.audienceId(),
          "ts", String.valueOf(ts)
      );
      String streamKey = "stream:question:events:" + roomId;
      redis.opsForStream().add(StreamRecords.mapBacked(streamBody).withStreamKey(streamKey));
      redis.opsForStream().trim(streamKey, STREAM_MAXLEN, true);
      log.debug("[STREAM] 질문 이벤트 스트림 추가됨 (streamKey={}, ts={})", streamKey, ts);
    } catch (Exception e) {
      log.error("[STREAM] 이벤트 스트림 추가 실패 (roomId={}, audienceId={})", roomId, request.audienceId(), e);
      throw new CustomException(AudienceQuestionErrorCode.STREAM_ERROR);
    }

    return response;
  }


  public List<CreateQuestionResponse> listRoom(String roomId, Long fromTs, Integer slide) {
    final String zRoom = "room:%s:questions".formatted(roomId);

    final double min = (fromTs == null) ? Double.NEGATIVE_INFINITY : Math.nextUp(fromTs.doubleValue());
    final double max = Double.POSITIVE_INFINITY;

    final String zKey = (slide == null)
        ? zRoom
        : "room:%s:page:%d:questions".formatted(roomId, slide);

    Set<ZSetOperations.TypedTuple<String>> tuples =
        redis.opsForZSet().rangeByScoreWithScores(zKey, min, max);

    if (tuples == null || tuples.isEmpty()) {
      log.debug("[REDIS] 질문 없음 (roomId={}, slide={})", roomId, slide);
      return List.of();
    }

    List<CreateQuestionResponse> result = new ArrayList<>(tuples.size());
    for (ZSetOperations.TypedTuple<String> t : tuples) {
      String qid = t.getValue();
      if (qid == null) continue;

      String hKey = "room:%s:question:%s".formatted(roomId, qid);
      Map<Object, Object> h = redis.opsForHash().entries(hKey);
      if (h == null || h.isEmpty()) continue;

      int s = Integer.parseInt((String) h.get("slide"));

      if (slide != null && !zKey.endsWith(":questions")) {
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

    result.sort(Comparator.comparingLong(CreateQuestionResponse::ts));
    return result;
  }

  private String genId() {
    return Long.toString(Instant.now().toEpochMilli(), 36) + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

    private void rateLimit(String roomId, String audienceId) {
        String key = "rate:%s:%s:q".formatted(roomId, audienceId);
        Long n = redis.opsForValue().increment(key);
        if (n != null && n == 1L) redis.expire(key, java.time.Duration.ofSeconds(5));
        if (n != null && n > 10) {
          log.warn("[RATE LIMIT] 요청이 너무 많습니다 (roomId={}, audienceId={}, count={})",
              roomId, audienceId, n);
          throw new CustomException(AudienceQuestionErrorCode.TOO_MANY_REQUESTS);
        }
    }
}