package line4thon.boini.audience.question.service;

import line4thon.boini.audience.question.dto.QuestionEvent;
import line4thon.boini.audience.question.dto.QuestionLikeEvent;
import line4thon.boini.audience.question.dto.requeset.CreateQuestionRequest;
import line4thon.boini.audience.question.dto.requeset.QuestionLikeRequest;
import line4thon.boini.audience.question.dto.requeset.QuestionInput;
import line4thon.boini.audience.question.dto.response.CreateQuestionResponse;
import line4thon.boini.audience.question.exception.AudienceQuestionErrorCode;
import line4thon.boini.global.common.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.script.DefaultRedisScript;
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
  private static final DefaultRedisScript<Long> LIKE_UPDATE_SCRIPT = new DefaultRedisScript<>(
      """
      if redis.call('EXISTS', KEYS[1]) == 0 then
        return -1
      end

      local status = redis.call('HGET', KEYS[1], 'status')
      if status ~= 'active' then
        return -2
      end

      if ARGV[2] == 'true' then
        redis.call('SADD', KEYS[2], ARGV[1])
      else
        redis.call('SREM', KEYS[2], ARGV[1])
      end

      local count = redis.call('SCARD', KEYS[2])
      redis.call('HSET', KEYS[1], 'likeCount', tostring(count))
      return count
      """,
      Long.class
  );
  private final RedisTemplate<String, String> redisTemplate;
  private final ClusterBroadcaster clusterBroadcaster;

  public CreateQuestionResponse create(String roomId, CreateQuestionRequest request) {

    ensureQuestionEnabled(roomId);
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
    h.put("status", "active");
    h.put("likeCount", "0");

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

    var response = new CreateQuestionResponse(
        id,
        roomId,
        request.slide(),
        request.audienceId(),
        request.content(),
        ts,
        0,
        false
    );
    String base = "/topic/p/" + roomId;
    broker.convertAndSend(base + "/public",    QuestionEvent.created(response));     // 청중/발표자 공통
    broker.convertAndSend(base + "/presenter", QuestionEvent.created(response));     // 발표자 전용(권한)

    log.info("[WS] 질문 실시간 전송 완료 (roomId={}, audienceId={}, slide={}, content='{}')",
        roomId, request.audienceId(), request.slide(), request.content());

    clusterBroadcaster.broadcast(roomId, new QuestionInput(id, request.content(), request.slide(), ts));

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

  public QuestionLikeEvent updateLike(String roomId, QuestionLikeRequest request) {
    String questionId = request.questionId();
    String hKey = "room:%s:question:%s".formatted(roomId, questionId);
    String likeSetKey = "room:%s:question:%s:likes".formatted(roomId, questionId);

    try {
      Long count = redis.execute(
          LIKE_UPDATE_SCRIPT,
          List.of(hKey, likeSetKey),
          request.audienceId(),
          Boolean.TRUE.equals(request.liked()) ? "true" : "false"
      );

      if (count == null || count == -1L) {
        log.warn("[QUESTION] 좋아요 대상 질문 없음 (roomId={}, questionId={})", roomId, questionId);
        throw new CustomException(AudienceQuestionErrorCode.INVALID_QUESTION_ID);
      }
      if (count == -2L) {
        log.warn("[QUESTION] 처리된 질문 좋아요 요청 차단 (roomId={}, questionId={})", roomId, questionId);
        throw new CustomException(AudienceQuestionErrorCode.QUESTION_NOT_ACTIVE);
      }

      int likeCount = count.intValue();

      QuestionLikeEvent event = QuestionLikeEvent.updated(
          questionId,
          request.audienceId(),
          Boolean.TRUE.equals(request.liked()),
          likeCount
      );
      broadcastLike(roomId, event);

      log.info("[QUESTION] 좋아요 변경 (roomId={}, questionId={}, audienceId={}, liked={}, likeCount={})",
          roomId, questionId, request.audienceId(), request.liked(), likeCount);
      return event;
    } catch (CustomException e) {
      throw e;
    } catch (Exception e) {
      log.error("[REDIS] 질문 좋아요 처리 실패 (roomId={}, questionId={}, audienceId={})",
          roomId, questionId, request.audienceId(), e);
      throw new CustomException(AudienceQuestionErrorCode.REDIS_ERROR);
    }
  }


  public List<CreateQuestionResponse> listRoom(
      String roomId,
      Long fromTs,
      Integer slide,
      String audienceId
  ) {
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

      String status = (String) h.get("status");
      if ("deleted".equals(status) || "completed".equals(status)) continue;

      long ts = Long.parseLong((String) h.get("ts"));
      result.add(new CreateQuestionResponse(
          (String) h.get("id"),
          (String) h.get("roomId"),
          s,
          (String) h.get("audienceId"),
          (String) h.get("content"),
          ts,
          parseLikeCount(h),
          isLikedByAudience(roomId, qid, audienceId)
      ));
    }

    result.sort(Comparator.comparingLong(CreateQuestionResponse::ts));
    return result;
  }

  private void broadcastLike(String roomId, QuestionLikeEvent event) {
    String base = "/topic/p/" + roomId;
    broker.convertAndSend(base + "/public", event);
    broker.convertAndSend(base + "/presenter", event);
  }

  private int parseLikeCount(Map<Object, Object> h) {
    Object raw = h.get("likeCount");
    if (raw == null) return 0;
    try {
      return Math.max(0, Integer.parseInt(String.valueOf(raw)));
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private boolean isLikedByAudience(String roomId, String questionId, String audienceId) {
    if (audienceId == null || audienceId.isBlank()) return false;
    String likeSetKey = "room:%s:question:%s:likes".formatted(roomId, questionId);
    return Boolean.TRUE.equals(redis.opsForSet().isMember(likeSetKey, audienceId));
  }

  private void ensureQuestionEnabled(String roomId) {
    String enabled = redisTemplate.opsForValue().get("room:%s:option:question".formatted(roomId));
    if ("false".equalsIgnoreCase(enabled)) {
      log.warn("[QUESTION] 비활성화된 질문 생성 요청 차단 (roomId={})", roomId);
      throw new CustomException(AudienceQuestionErrorCode.QUESTION_DISABLED);
    }
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
