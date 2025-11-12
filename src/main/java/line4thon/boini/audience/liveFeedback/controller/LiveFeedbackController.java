package line4thon.boini.audience.liveFeedback.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import line4thon.boini.audience.sticker.dto.request.StickerRequest;
import line4thon.boini.audience.sticker.dto.response.StickerLoadResponse;
import line4thon.boini.audience.sticker.dto.response.StickerResponse;
import line4thon.boini.global.common.response.BaseResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@RestController("/api/liveFeedback")
@RequiredArgsConstructor
@Tag(name = "LiveFeedback", description = "실시간 피드백 관련")
public class LiveFeedbackController {

    private final RedisTemplate<String, String> redisTemplate;

    @Operation(
            summary = "발표자용 - 새로고침 시 해당 슬라이드의 실시간 피드백을 가져옴",
            description = """
          """
    )
    @GetMapping("/{roomId}/slide/{slideNumber}")
    public Object getLiveFeedback(@PathVariable String roomId,
                                  @PathVariable String slideNumber) {

        int slideNum;
        try {
            slideNum = Integer.parseInt(slideNumber);
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body("slide는 숫자여야 합니다.");
        }

        String key = String.format("room:%s:liveFeedback:slide:%d", roomId, slideNum);
        return redisTemplate.opsForHash().get(key,"message");
    }

}
