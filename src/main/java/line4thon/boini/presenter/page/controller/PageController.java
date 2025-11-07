package line4thon.boini.presenter.page.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import line4thon.boini.audience.room.dto.response.JoinResponse;
import line4thon.boini.global.common.exception.CustomException;
import line4thon.boini.global.common.response.BaseResponse;
import line4thon.boini.presenter.page.dto.request.ChangeAudiencePageRequest;
import line4thon.boini.presenter.page.dto.request.ChangePageRequest;
import line4thon.boini.presenter.page.dto.response.ChangeAudiencePageResponse;
import line4thon.boini.presenter.page.dto.response.ChangePageResponse;
import line4thon.boini.presenter.page.dto.response.SlideAudienceCountResponse;
import line4thon.boini.presenter.page.exception.PageErrorCode;
import line4thon.boini.presenter.page.service.PageService;
import line4thon.boini.presenter.room.exception.PresenterErrorCode;
import line4thon.boini.presenter.room.service.RoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class PageController {

    private final SimpMessagingTemplate messagingTemplate;
    private final RedisTemplate<String, String> redisTemplate;
    private final PageService pageService;

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
    public BaseResponse handlePageChangeAudience(@DestinationVariable String sessionId, ChangeAudiencePageRequest msg) {

        // Redis 관리
        pageService.updateAudiencePage(sessionId, msg);

        return BaseResponse.success("페이지가 성공적으로 변경되었습니다.", new ChangeAudiencePageResponse(
                msg.getAudienceId(),
                msg.getBeforePage(),
                msg.getChangedPage(),
                LocalDateTime.now()
        ));
    }

    @MessageMapping("/presentation/{sessionId}/focusOn")
    public ResponseEntity handleFocusOn(@DestinationVariable String sessionId) {

        pageService.FocusOn(sessionId);

        return ResponseEntity.ok("정상적으로 집중이 유도되었습니다.");
    }

}
