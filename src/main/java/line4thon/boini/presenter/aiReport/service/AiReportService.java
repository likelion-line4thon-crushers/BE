package line4thon.boini.presenter.aiReport.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import line4thon.boini.presenter.aiReport.dto.response.MostReactionStickerResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class AiReportService {

    private final RedisTemplate<String, String> redisTemplate;
    private final RedisTemplate<String, Object> objectRedisTemplate;
    private final ObjectMapper objectMapper;

    public List<MostReactionStickerResponse> getMostReactionSticker(String roomId) {
        String key = "room:" + roomId + ":stickers";

        log.info("=== getMostReactionSticker 시작 ===");
        log.info("Redis Stream Key: {}", key);

        // 1) Stream 전체 조회
        List<MapRecord<String, Object, Object>> records =
                objectRedisTemplate.opsForStream().range(key, Range.unbounded());

        log.info("조회된 Stream 레코드 개수: {}", records.size());

        // emoji → slide → count
        Map<Integer, Map<Integer, Long>> countMap = new HashMap<>();

        for (MapRecord<String, Object, Object> record : records) {
            log.info("Raw record value: {}", record.getValue());  // 실제 Redis에서 가져온 값 확인

            Map<String, Object> value = objectMapper.convertValue(record.getValue(), new TypeReference<>(){});
            log.info("Converted record value: {}", value);

            try {
                int emoji = (Integer) value.get("emoji");
                int slide = Integer.parseInt(value.get("slide").toString());
                log.info("Parsed emoji={}, slide={}", emoji, slide);

                countMap
                        .computeIfAbsent(emoji, k -> new HashMap<>())
                        .merge(slide, 1L, Long::sum);
            } catch (Exception e) {
                log.error("레코드 파싱 실패: {}", value, e);
            }
        }

        log.info("CountMap: {}", countMap);

        List<MostReactionStickerResponse> result = new ArrayList<>();

        for (Integer emoji : countMap.keySet()) {
            Map<Integer, Long> slideCounts = countMap.get(emoji);

            List<Map.Entry<Integer, Long>> sorted =
                    slideCounts.entrySet().stream()
                            .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                            .toList();

            int topSlide = sorted.get(0).getKey();
            long topCount = sorted.get(0).getValue();

            int secondSlide = sorted.size() > 1 ? sorted.get(1).getKey() : -1;
            long secondCount = sorted.size() > 1 ? sorted.get(1).getValue() : 0;

            log.info("Emoji {}: Top Slide={} ({}회), Second Slide={} ({}회)", emoji, topSlide, topCount, secondSlide, secondCount);

            result.add(new MostReactionStickerResponse(
                    emoji,
                    topSlide, topCount,
                    secondSlide, secondCount
            ));
        }

        log.info("최종 결과 리스트 크기: {}", result.size());
        log.info("=== getMostReactionSticker 종료 ===");

        return result;
    }



}
