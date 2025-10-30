package line4thon.boini.presenter.page.controller;

import line4thon.boini.audience.room.dto.response.JoinResponse;
import line4thon.boini.global.common.exception.CustomException;
import line4thon.boini.global.common.response.BaseResponse;
import line4thon.boini.presenter.page.dto.request.ChangePageRequest;
import line4thon.boini.presenter.page.dto.response.ChangePageResponse;
import line4thon.boini.presenter.page.exception.PageErrorCode;
import line4thon.boini.presenter.page.service.PageService;
import line4thon.boini.presenter.room.exception.PresenterErrorCode;
import line4thon.boini.presenter.room.service.RoomService;
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
    private final PageService pageService;


    public PageController(SimpMessagingTemplate messagingTemplate,
                          RedisTemplate<String, String> redisTemplate,
                          PageService pageService) {
        this.messagingTemplate = messagingTemplate;
        this.redisTemplate = redisTemplate;
        this.pageService = pageService;
    }


    // 발표자가 페이지를 바꿀 때 (클라이언트 -> /app/presentation/{uuid}/pageChange/presenter)
    @MessageMapping("/presentation/{sessionId}/pageChange/presenter")
    public BaseResponse handlePageChangePresenter(@DestinationVariable String sessionId, ChangePageRequest msg) {
        // Redis 관리
        pageService.updateCurrentPage(sessionId, msg);

        return BaseResponse.success("페이지가 성공적으로 변경되었습니다.", new ChangePageResponse(
                msg.getBeforePage(),
                msg.getChangedPage(),
                LocalDateTime.now()
        ));
    }

    @MessageMapping("/presentation/{sessionId}/pageChange/audience")
    public BaseResponse handlePageChangeAudience(@DestinationVariable String sessionId, ChangePageRequest msg) {

        // Redis 관리
        pageService.updateAudiencePage(sessionId, msg);

        return BaseResponse.success("페이지가 성공적으로 변경되었습니다.", new ChangePageResponse(
                msg.getBeforePage(),
                msg.getChangedPage(),
                LocalDateTime.now()
        ));
    }


}
