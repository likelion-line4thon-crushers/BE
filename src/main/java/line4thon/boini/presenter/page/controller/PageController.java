package line4thon.boini.presenter.page.controller;

import line4thon.boini.audience.room.dto.response.JoinResponse;
import line4thon.boini.global.common.exception.CustomException;
import line4thon.boini.global.common.response.BaseResponse;
import line4thon.boini.presenter.page.dto.request.ChangePageRequest;
import line4thon.boini.presenter.page.dto.response.ChangePageResponse;
import line4thon.boini.presenter.page.exception.PageErrorCode;
import line4thon.boini.presenter.room.exception.PresenterErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Controller
public class PageController {

    private final SimpMessagingTemplate messagingTemplate;
    private final RedisTemplate<String, String> redisTemplate;


    public PageController(SimpMessagingTemplate messagingTemplate,
                                        RedisTemplate<String, String> redisTemplate) {
        this.messagingTemplate = messagingTemplate;
        this.redisTemplate = redisTemplate;
    }


    // 발표자가 페이지를 바꿀 때 (클라이언트 -> /app/presentation/{uuid}/pageChange/presenter)
    @MessageMapping("/presentation/{sessionId}/pageChange/presenter")
    public BaseResponse handlePageChange(@DestinationVariable String sessionId, ChangePageRequest msg) {
        // 권한 체크(발표자 확인) 등은 여기서 수행 가능
//        presentationService.updateCurrentPage(uuid, msg.getPage());

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

            return BaseResponse.success("페이지가 성공적으로 변경되었습니다.", new ChangePageResponse(
                    msg.getBeforePage(),
                    msg.getChangedPage(),
                    LocalDateTime.now()
            ));

        } catch (Exception e){
            log.error("페이지 전환 실패: roomId={}, err={}", sessionId, e.toString());
            throw new CustomException(PageErrorCode.NOT_RECEIVE_PAGE_DATA);
        }

    }
}
