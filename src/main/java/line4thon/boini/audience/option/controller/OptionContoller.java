package line4thon.boini.audience.option.controller;

import line4thon.boini.audience.option.dto.request.OptionRequest;
import line4thon.boini.audience.option.dto.response.UnlockResponse;
import line4thon.boini.audience.option.service.OptionService;
import line4thon.boini.global.common.response.BaseResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class OptionContoller {

    private final SimpMessagingTemplate messagingTemplate;
    private final RedisTemplate<String, String> redisTemplate;
    private final OptionService optionService;

    // 발표자가 방 옵션을 변경할 때 (클라이언트 -> /app/presentation/{sessionId}/option)
    @MessageMapping("/presentation/{sessionId}/option")
    public BaseResponse handleChangeOption(@DestinationVariable String sessionId, OptionRequest msg) {

//        optionService.changeOption(sessionId, msg);

        redisTemplate.opsForValue().set("room:"+sessionId+":option:sticker", msg.getSticker());
        redisTemplate.opsForValue().set("room:"+sessionId+":option:question", msg.getQuestion());
        redisTemplate.opsForValue().set("room:"+sessionId+":option:feedback", msg.getFeedback());

        messagingTemplate.convertAndSend("/topic/presentation/" + sessionId + "/option", msg);
        log.info("옵션 수정 완료 : roomId={}, sticker={}, question={}, feedback={}", sessionId, msg.getSticker(), msg.getQuestion(), msg.getFeedback());

        return BaseResponse.success("옵션이 성공적으로 변경되었습니다.", msg);
    }

    // 발표자가 방 옵션 중 ppt 잠금을 변경할 때 (클라이언트 -> /app/presentation/{sessionId}/option/unlock)
    @MessageMapping("/presentation/{sessionId}/option/unlock/{unlock}")
    public BaseResponse handleChangeOptionUnlock(@DestinationVariable String sessionId, @DestinationVariable("unlock") String unlock) {

        redisTemplate.opsForValue().set("room:"+sessionId+":option:slideUnlock", unlock);
        String totalPage = redisTemplate.opsForValue().get("room:"+sessionId+":totalPage");
        String maxSlide = redisTemplate.opsForValue().get("room:"+sessionId+":maxSlide");
        String presenterPage = redisTemplate.opsForValue().get("room:"+sessionId+":presenterPage");

        UnlockResponse msg = new UnlockResponse().builder()
                .maxRevealedPage(maxSlide)
                .revealAllSlides(unlock)
                .totalPages(totalPage)
                .presenterPage(presenterPage)
                .build();

        messagingTemplate.convertAndSend("/topic/presentation/" + sessionId + "/option/unlock", msg);
        log.info("페이지 잠금 옵션 수정 완료 : roomId={}, maxSlide={}, unlock={}, totalPage={}, presenterPage={}", sessionId, maxSlide, unlock, totalPage, presenterPage );

        return BaseResponse.success("페이지 잠금 옵션이 성공적으로 변경되었습니다.", msg);
    }

}
