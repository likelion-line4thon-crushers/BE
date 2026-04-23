package line4thon.boini.audience.question.service;

import line4thon.boini.audience.question.dto.QuestionStatusEvent;
import line4thon.boini.audience.question.dto.response.CreateQuestionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionManageService {

  private final StringRedisTemplate redis;
  private final SimpMessagingTemplate broker;

  /**
   * 질문을 완료 처리한다.
   * - Redis Hash의 status를 "completed"로 변경
   * - room:{roomId}:questions:completed Set에 questionId 추가
   * - 청중/발표자에게 QUESTION_COMPLETED 이벤트 broadcast
   */
  public void complete(String roomId, String questionId) {
    String hKey = "room:%s:question:%s".formatted(roomId, questionId);
    redis.opsForHash().put(hKey, "status", "completed");
    redis.opsForSet().add("room:" + roomId + ":questions:completed", questionId);

    var evt = QuestionStatusEvent.completed(questionId);
    String base = "/topic/p/" + roomId;
    broker.convertAndSend(base + "/public", evt);
    broker.convertAndSend(base + "/presenter", evt);

    log.info("[QUESTION] 완료 처리 (roomId={}, questionId={})", roomId, questionId);
  }

  /**
   * 질문을 soft delete 처리한다.
   * - Redis Hash의 status를 "deleted"로 변경
   * - room:{roomId}:questions:deleted Set에 questionId 추가
   * - 청중/발표자에게 QUESTION_DELETED 이벤트 broadcast → 실시간 채팅창에서 즉시 제거
   */
  public void delete(String roomId, String questionId) {
    String hKey = "room:%s:question:%s".formatted(roomId, questionId);
    redis.opsForHash().put(hKey, "status", "deleted");
    redis.opsForSet().add("room:" + roomId + ":questions:deleted", questionId);

    var evt = QuestionStatusEvent.deleted(questionId);
    String base = "/topic/p/" + roomId;
    broker.convertAndSend(base + "/public", evt);
    broker.convertAndSend(base + "/presenter", evt);

    log.info("[QUESTION] 삭제 처리 (roomId={}, questionId={})", roomId, questionId);
  }

  /**
   * 완료 처리된 질문 목록을 반환한다.
   * - room:{roomId}:questions:completed Set에서 ID 조회
   * - 각 ID에 해당하는 Hash 데이터 fetch 후 ts 오름차순 정렬
   */
  public List<CreateQuestionResponse> listCompleted(String roomId) {
    Set<String> completedIds = redis.opsForSet().members("room:" + roomId + ":questions:completed");
    if (completedIds == null || completedIds.isEmpty()) return List.of();

    List<CreateQuestionResponse> result = new ArrayList<>();
    for (String qid : completedIds) {
      Map<Object, Object> h = redis.opsForHash().entries("room:%s:question:%s".formatted(roomId, qid));
      if (h == null || h.isEmpty()) continue;
      result.add(new CreateQuestionResponse(
          (String) h.get("id"),
          (String) h.get("roomId"),
          Integer.parseInt((String) h.get("slide")),
          (String) h.get("audienceId"),
          (String) h.get("content"),
          Long.parseLong((String) h.get("ts"))
      ));
    }

    result.sort(Comparator.comparingLong(CreateQuestionResponse::ts));
    return result;
  }
}
