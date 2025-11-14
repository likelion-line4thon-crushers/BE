package line4thon.boini.audience.sticker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import line4thon.boini.audience.sticker.dto.request.StickerRequest;
import line4thon.boini.audience.sticker.dto.response.StickerResponse;
import line4thon.boini.audience.sticker.exception.StickerErrorCode;
import line4thon.boini.global.common.exception.CustomException;
import line4thon.boini.global.common.exception.GlobalErrorCode;
import line4thon.boini.global.common.exception.model.BaseErrorCode;
import line4thon.boini.presenter.page.exception.PageErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.support.NullValue;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;


@Service
@Slf4j
@RequiredArgsConstructor
public class StickerService {

    private final SimpMessagingTemplate messagingTemplate;
    private final RedisTemplate<String, String> redisTemplate;
    private final RedisTemplate<String, Object> objectRedisTemplate;

    public void sendStickerMessage(String sessionId, StickerRequest msg) {


        try{
            String key = "room:" + sessionId + ":stickers";

            Map<String, Object> fields = new HashMap<>();
            fields.put("emoji", msg.getEmoji());
            fields.put("audienceId", msg.getAudienceID());
            fields.put("createdAt", msg.getCreated_at());
            fields.put("x", msg.getX());
            fields.put("y", msg.getY());
            fields.put("slide", msg.getSlide());

            objectRedisTemplate.opsForStream().add(key, fields);

            String key3 = "room:" + sessionId + ":liveFeedback:slide:" + msg.getSlide();


            String key4 = "room:" + sessionId + ":liveFeedback:slide:" + msg.getSlide() + ":emoji:" + msg.getEmoji() + ":audience";
            redisTemplate.opsForSet().add(key4, msg.getAudienceID());
            String slideEmojiPeopleCounts = redisTemplate.opsForSet().size(key4).toString();
            Object ob = redisTemplate.opsForHash().get(key3, "mostPeopleCounts");
            int mostPeopleCounts;
            if (ob != null) {
                mostPeopleCounts = Integer.parseInt(ob.toString());
            }
            else{
                mostPeopleCounts=0;
            }
            int totalUser = Integer.parseInt(Objects.requireNonNull(redisTemplate.opsForSet().size("room:" + sessionId + ":audience:online")).toString()) ;

            int slideEmojiPeopleCount = Integer.parseInt(slideEmojiPeopleCounts);

            if((totalUser/2.0)<=slideEmojiPeopleCount && mostPeopleCounts<slideEmojiPeopleCount){

                log.info("totalUser={}, slideEmojiPeopleCount={}, mostPeopleCounts={}", totalUser, slideEmojiPeopleCount, mostPeopleCounts);

                String status = "FIRST";

                String emojiText = switch (msg.getEmoji()) {
                    case 1 -> "재미있는";
                    case 2 -> "놀라운";
                    case 3 -> "궁금한";
                    case 4 -> "신나는";
                    case 5 -> "열받는";
                    case 6 -> "슬픈";
                    case 7 -> "O";
                    case 8 -> "X";
                    default -> "기타 이모지";
                };
                String message = "청중의 절반 이상이 \'" + emojiText + "\' 반응을 했어요!";


                redisTemplate.opsForHash().put(key3, "status", status);
                redisTemplate.opsForHash().put(key3, "message", message);
                redisTemplate.opsForHash().put(key3, "mostPeopleCounts", slideEmojiPeopleCounts);

                messagingTemplate.convertAndSend("/topic/presentation/"+sessionId+"/liveFeedback",message);

            }


            String key2 = "room:" + sessionId + ":liveFeedback:slide:" + msg.getSlide() + ":emoji:counts";
            redisTemplate.opsForHash().increment(key2, "emoji:"+msg.getEmoji(), 1);

            Object statusObj = redisTemplate.opsForHash().get(key3, "status");
            String status = statusObj.toString();

            if (!"FIRST".equals(status)) {
                Long fieldCount = redisTemplate.opsForHash().size(key2);

                if(fieldCount >= 2){
                    Map<Object, Object> emojiCounts = redisTemplate.opsForHash().entries(key2);

                    String maxEmoji = null;
                    long maxCount = Long.MIN_VALUE;

                    for (Map.Entry<Object, Object> entry : emojiCounts.entrySet()) {
                        String field = entry.getKey().toString();
                        long count = Long.parseLong(entry.getValue().toString());

                        if (count > maxCount) {
                            maxCount = count;
                            maxEmoji = field;
                        }
                    }

                    String emojiNumber = Objects.requireNonNull(maxEmoji).split(":")[1];

                    if (maxEmoji != null) {
                        System.out.println("가장 많이 선택된 이모지: " + emojiNumber + " (" + maxCount + ")");
                    }

                    status ="SECOND";
                    String emojiText = switch (Integer.parseInt(emojiNumber)) {
                        case 1 -> "'재미있는'";
                        case 2 -> "'놀라운'";
                        case 3 -> "'궁금한'";
                        case 4 -> "'신나는'";
                        case 5 -> "'열받는'";
                        case 6 -> "'슬픈'";
                        case 7 -> "'O'";
                        case 8 -> "'X'";
                        default -> "'기타 이모지'";
                    };
                    String message = emojiText+" 반응이 두드러지고 있어요!";

                    redisTemplate.opsForHash().put(key3, "status", status);
                    redisTemplate.opsForHash().put(key3, "message", message);

                    messagingTemplate.convertAndSend("/topic/presentation/"+sessionId+"/liveFeedback",message);

                }
            }



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