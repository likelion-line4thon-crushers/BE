package line4thon.boini.presenter.aiReport.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import line4thon.boini.presenter.aiReport.dto.response.MostReactionStickerResponse;
import line4thon.boini.presenter.aiReport.dto.response.MostRevisitResponse;
import line4thon.boini.presenter.aiReport.dto.response.RevisitResponse;
import line4thon.boini.presenter.page.service.PageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class AiReportService {

    private final RedisTemplate<String, String> redisTemplate;
    private final RedisTemplate<String, Object> objectRedisTemplate;
    private final ObjectMapper objectMapper;
    private final PageService pageService;

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

    public MostRevisitResponse getMostRevisit(String roomId) {

        List<RevisitResponse> revisitList = getRevisit(roomId);

        // 가장 많이 재방문된 슬라이드 찾기
        RevisitResponse most = revisitList.stream()
                .max(Comparator.comparingInt(RevisitResponse::getRevisits))
                .orElse(new RevisitResponse(0, 0));

        int slide = most.getSlide();

        String usersKey = "room:" + roomId + ":revisit:users:" + slide;

        // 재방문한 사용자 수
        Long uniqueUsers = redisTemplate.opsForSet().size(usersKey);

        // 2번 이상 재방문한 사용자 수 계산
        int multiRevisitUsers = 0;
        Set<String> users = redisTemplate.opsForSet().members(usersKey);
        if (users != null) {
            for (String user : users) {
                String userCountKey = "room:" + roomId + ":revisit:user:" + slide + ":" + user;
                String count = redisTemplate.opsForValue().get(userCountKey);
                if (count != null && Integer.parseInt(count) >= 2) {
                    multiRevisitUsers++;
                }
            }
        }

        String key4 = "room:" + roomId + ":enterAudienceCount";
        int totalAudienceCount = Integer.parseInt(redisTemplate.opsForValue().get(key4));

        return MostRevisitResponse.builder()
                .slide(slide)
                .totalRevisits(most.getRevisits())
                .totalAudienceCount(totalAudienceCount)
                .uniqueUsers(uniqueUsers != null ? uniqueUsers.intValue() : 0)
                .multiRevisitUsers(multiRevisitUsers)
                .build();
    }


    public List<RevisitResponse> getRevisit(String roomId) {
        int slides = pageService.countSlideKeys(roomId); // 전체 슬라이드 개수
        List<RevisitResponse> revisitList = new ArrayList<>();

        for (int slide = 1; slide <= slides; slide++) { // 슬라이드 번호 1부터 시작 가정
            String key = "room:" + roomId + ":revisit:" + slide;

            // Redis에서 revisit 값 가져오기
            String revisitStr = redisTemplate.opsForValue().get(key);

            // 값이 없으면 0으로 처리
            int revisits = (revisitStr != null) ? Integer.parseInt(revisitStr) : 0;

            // RevisitResponse 생성 후 리스트에 추가
            revisitList.add(RevisitResponse.builder()
                    .slide(slide)
                    .revisits(revisits)
                    .build());
        }

        return revisitList;
    }


}
