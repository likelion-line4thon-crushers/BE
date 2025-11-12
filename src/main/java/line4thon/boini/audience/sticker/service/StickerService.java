package line4thon.boini.audience.sticker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import line4thon.boini.audience.sticker.dto.request.StickerRequest;
import line4thon.boini.audience.sticker.dto.response.StickerResponse;
import line4thon.boini.audience.sticker.exception.StickerErrorCode;
import line4thon.boini.global.common.exception.CustomException;
import line4thon.boini.presenter.page.exception.PageErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;


@Service
@Slf4j
@RequiredArgsConstructor
public class StickerService {

    private final SimpMessagingTemplate messagingTemplate;
    private final RedisTemplate<String, String> redisTemplate;
    private final RedisTemplate<String, Object> objectRedisTemplate;

    public void sendStickerMessage(String sessionId, StickerRequest msg) {


        try{
            // Redis Stream Key
            String key = "room:" + sessionId + ":stickers";

//            ObjectMapper mapper = new ObjectMapper();

            // 저장할 필드 구성
            Map<String, Object> fields = new HashMap<>();
            fields.put("emoji", msg.getEmoji());
            fields.put("audienceId", msg.getAudienceID());
            fields.put("createdAt", msg.getCreated_at());
            fields.put("x", msg.getX());
            fields.put("y", msg.getY());
            fields.put("slide", msg.getSlide());

            // Redis Stream에 추가 (XADD)
            objectRedisTemplate.opsForStream().add(key, fields);
            // JSON으로 변환 후 저장
//            String json = mapper.writeValueAsString(fields);
//            redisTemplate.opsForStream().add(key, Map.of("data", json));

            StickerResponse response = StickerResponse.builder()
                    .emoji(msg.getEmoji())
                    .x(msg.getX())
                    .y(msg.getY())
                    .slide(msg.getSlide())
                    .created_at(msg.getCreated_at())
                    .build();



            // 모든 구독자에게 브로드캐스트: /topic/presentation/{sessionId}
            messagingTemplate.convertAndSend("/topic/presentation/" + sessionId + "/reactions", response);
            log.info("스티커 부착 완료: roomId={}, emoji={}, x={}, y={}, slide={}, created_at={}", sessionId, msg.getEmoji(), msg.getX(), msg.getY(), msg.getSlide(), msg.getCreated_at());

        } catch (Exception e){
            log.error("스티커 부착 실패: roomId={}, err={}", sessionId, e.toString());
            throw new CustomException(StickerErrorCode.NOT_SEND_REACTION_STICKER);
        }

    }

}