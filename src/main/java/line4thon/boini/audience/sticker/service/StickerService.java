package line4thon.boini.audience.sticker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import line4thon.boini.audience.sticker.dto.request.StickerRequest;
import line4thon.boini.audience.sticker.dto.response.StickerResponse;
import line4thon.boini.audience.sticker.exception.StickerErrorCode;
import line4thon.boini.global.common.exception.CustomException;
import line4thon.boini.presenter.page.exception.PageErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;


@Service
@Slf4j
public class StickerService {

    private final SimpMessagingTemplate messagingTemplate;
    private final RedisTemplate<String, String> redisTemplate;


    public StickerService(SimpMessagingTemplate messagingTemplate,
                          RedisTemplate<String, String> redisTemplate) {
        this.messagingTemplate = messagingTemplate;
        this.redisTemplate = redisTemplate;
    }




    public void sendStickerMessage(String sessionId, StickerRequest msg) {


        try{
            // Redis Stream Key
            String key = "room:" + sessionId + ":stickers";

//            ObjectMapper mapper = new ObjectMapper();

            // 저장할 필드 구성
            Map<String, String> fields = new HashMap<>();
            fields.put("emoji", String.valueOf(msg.getEmoji()));
            fields.put("audienceId", msg.getAudienceID());
            fields.put("createdAt", String.valueOf(msg.getCreated_at()));
            fields.put("x", String.valueOf(msg.getX()));
            fields.put("y", String.valueOf(msg.getY()));
            fields.put("slide", String.valueOf(msg.getSlide()));

            // Redis Stream에 추가 (XADD)
            redisTemplate.opsForStream().add(key, fields);
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