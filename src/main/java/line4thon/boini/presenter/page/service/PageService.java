package line4thon.boini.presenter.page.service;

import line4thon.boini.global.common.exception.CustomException;
import line4thon.boini.global.common.response.BaseResponse;
import line4thon.boini.presenter.page.dto.request.ChangePageRequest;
import line4thon.boini.presenter.page.dto.response.ChangePageResponse;
import line4thon.boini.presenter.page.exception.PageErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Slf4j
public class PageService {

    private final SimpMessagingTemplate messagingTemplate;
    private final RedisTemplate<String, String> redisTemplate;


    public PageService(SimpMessagingTemplate messagingTemplate,
                          RedisTemplate<String, String> redisTemplate) {
        this.messagingTemplate = messagingTemplate;
        this.redisTemplate = redisTemplate;
    }

    public void updateCurrentPage(String sessionId, ChangePageRequest msg) {
        try{
            // Redis Stream 저장
//            Map<String, String> event = Map.of(
//                    "sessionId", sessionId,
//                    "type", "PRESENTER_PAGE_CHANGE",
//                    "page", String.valueOf(msg.getChangedPage()),
//                    "timestamp", String.valueOf(System.currentTimeMillis())
//            );
//            redisTemplate.opsForStream().add("page_events", event);


            // 모든 구독자에게 브로드캐스트: /topic/presentation/{sessionId}
            messagingTemplate.convertAndSend("/topic/presentation/" + sessionId + "/pageChange", msg);
            log.info("페이지 전환 브로드캐스트 완료: roomId={}, beforePage={}, changedPage={}", sessionId, msg.getBeforePage(), msg.getChangedPage());

        } catch (Exception e){
            log.error("페이지 전환 실패: roomId={}, err={}", sessionId, e.toString());
            throw new CustomException(PageErrorCode.NOT_RECEIVE_PAGE_DATA);
        }
    }

    public void updateAudiencePage(String sessionId, ChangePageRequest msg) {
        try{
            // Redis Stream 저장
//            Map<String, String> event = Map.of(
//                    "sessionId", sessionId,
//                    "type", "PRESENTER_PAGE_CHANGE",
//                    "page", String.valueOf(msg.getChangedPage()),
//                    "timestamp", String.valueOf(System.currentTimeMillis())
//            );
//            redisTemplate.opsForStream().add("page_events", event);
            //사용자 Redis 현재 위치 수정
            //각 페이지 별로 Key에서 집합 수정
            log.info("페이지 전환 완료: roomId={}, beforePage={}, changedPage={}", sessionId, msg.getBeforePage(), msg.getChangedPage());

//            System.out.println("페이지 전환 완료: roomId=" + sessionId +
//                    ", beforePage=" + msg.getBeforePage() +
//                    ", changedPage=" + msg.getChangedPage());

        } catch (Exception e){
            log.error("페이지 전환 실패: roomId={}, err={}", sessionId, e.toString());
            throw new CustomException(PageErrorCode.CHANGE_PAGE_ERROR);
        }
    }


}
