package line4thon.boini.audience.sticker.controller;

import line4thon.boini.audience.sticker.dto.request.StickerRequest;
import line4thon.boini.audience.sticker.dto.response.StickerResponse;
import line4thon.boini.audience.sticker.service.StickerService;
import line4thon.boini.global.common.response.BaseResponse;
import line4thon.boini.presenter.page.dto.request.ChangePageRequest;
import line4thon.boini.presenter.page.dto.response.ChangePageResponse;
import line4thon.boini.presenter.page.service.PageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;


@Slf4j
@Controller
public class StickerController {

    private final SimpMessagingTemplate messagingTemplate;
    private final RedisTemplate<String, String> redisTemplate;
    private final StickerService stickerService;


    public StickerController(SimpMessagingTemplate messagingTemplate,
                          RedisTemplate<String, String> redisTemplate,
                          StickerService stickerService) {
        this.messagingTemplate = messagingTemplate;
        this.redisTemplate = redisTemplate;
        this.stickerService = stickerService;
    }


    // 청중이 리액션 스티커 달 때 (클라이언트 -> /app/presentation/{sessionId}/reaction)
    @MessageMapping("/presentation/{sessionId}/reaction")
    public BaseResponse handleSendReactionSticker(@DestinationVariable String sessionId, StickerRequest msg) {

        stickerService.sendStickerMessage(sessionId, msg);

        return BaseResponse.success("스티커가 성공적으로 전송되었습니다.", new StickerResponse(
                msg.getEmoji(),
                LocalDateTime.now(),
                msg.getX(),
                msg.getY(),
                msg.getSlide()
        ));
    }
}