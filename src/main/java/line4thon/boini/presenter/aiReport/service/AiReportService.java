package line4thon.boini.presenter.aiReport.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import line4thon.boini.presenter.aiReport.dto.response.MostReactionStickerResponse;
import line4thon.boini.presenter.aiReport.dto.response.MostRevisitResponse;
import line4thon.boini.presenter.aiReport.dto.response.ReportTopResponse;
import line4thon.boini.presenter.aiReport.dto.response.RevisitResponse;
import line4thon.boini.presenter.page.service.PageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
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
    private final StringRedisTemplate redis;


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

            Map<String, Object> value = objectMapper.convertValue(record.getValue(), new TypeReference<>() {
            });
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

            revisitList.add(RevisitResponse.builder()
                    .slide(slide)
                    .revisits(revisits)
                    .build());
        }

        return revisitList;
    }

    public ReportTopResponse getReportTop(String roomId) {

        String key1 = "room:" + roomId + ":stickers";
        String key2 = "room:" + roomId + ":questionCount";

        Long emoji = objectRedisTemplate.opsForStream().size(key1);
        System.out.println("emoji Stream size = " + emoji);

        String question = redisTemplate.opsForValue().get(key2);
        System.out.println("question Stream size = " + question);

        Long focusSlide = Long.valueOf(getFocusSlide(roomId));

        return ReportTopResponse.builder()
                .totalEmoji(emoji)
                .totalQuestion((question != null) ? Long.parseLong(question) : 0L)
                .focusSlide(focusSlide)
                .build();
    }

    public int getFocusSlide(String roomId) {

        int slides = pageService.countSlideKeys(roomId); // 전체 슬라이드 개수
        int[] questionScores = new int[slides]; // index: 슬라이드 번호, 값: 점수

        List<Long> slidesWithMostQuestions = getSlidesWithMostQuestions(roomId);

        // 최다 질문 점수 +5
        for (Long slideNum : slidesWithMostQuestions) {
            if (slideNum > 0 && slideNum <= slides) {
                questionScores[slideNum.intValue() - 1] += 5;
            }
        }

        log.info("최다 질문 슬라이드 : roomId={}, slide={}", roomId, slidesWithMostQuestions);

        List<RevisitResponse> revisitList = getRevisit(roomId);

// 1) 가장 많이 재방문한 횟수
        int maxRevisits = revisitList.stream()
                .mapToInt(RevisitResponse::getRevisits)
                .max()
                .orElse(0);

        if (maxRevisits > 0) { // maxRevisits가 0보다 클 때만 점수 적용
            // 2) maxRevisits 가진 슬라이드 모두 리스트로 추출
            List<Integer> mostRevisitedSlides = revisitList.stream()
                    .filter(r -> r.getRevisits() == maxRevisits)
                    .map(RevisitResponse::getSlide)
                    .sorted()
                    .toList();

            // 최다 방문 점수 +4
            for (Integer slideNum : mostRevisitedSlides) {
                if (slideNum > 0 && slideNum <= slides) {
                    questionScores[slideNum - 1] += 4;
                }
            }
            log.info("최다 방문수 슬라이드 : roomId={}, slide={}", roomId, mostRevisitedSlides);
        }



        List<Integer> mostReactionSlides = findMostReactionSlides(roomId);

        // 최다 이모지 점수 +3
        for (Integer slideNum : mostReactionSlides) {
            if (slideNum > 0 && slideNum <= slides) {
                questionScores[slideNum - 1] += 3;
            }
        }

        log.info("최다 이모지 슬라이드 : roomId={}, slide={}", roomId, mostReactionSlides);
        log.info("최종 스코어 : roomId={}, score={}", roomId, questionScores);

        int maxIndex = 0;
        for (int i = 1; i < questionScores.length; i++) {
            if (questionScores[i] > questionScores[maxIndex]) {
                maxIndex = i;
            }
        }

        return maxIndex + 1; // 0-based index 보정
    }

    // 가장 질문 수 많은 슬라이드 반환 (안전하게 수정)
    public List<Long> getSlidesWithMostQuestions(String roomId) {
        String pattern = "room:%s:page:*:questions".formatted(roomId);

        Set<String> keys = redis.keys(pattern);
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }

        Map<Long, Long> slideCountMap = new HashMap<>();

        for (String key : keys) {
            String[] parts = key.split(":");
            if (parts.length < 4) {
                log.warn("Unexpected key format: {}", key);
                continue; // 형식이 맞지 않으면 건너뜀
            }

            try {
                long slide = Long.parseLong(parts[3]);
                Long count = redis.opsForZSet().zCard(key);
                slideCountMap.put(slide, (count != null ? count : 0L));
            } catch (NumberFormatException e) {
                log.warn("Invalid slide number in key: {}", key);
            }
        }

        long maxCount = slideCountMap.values().stream()
                .mapToLong(Long::longValue)
                .max()
                .orElse(0L);

        return slideCountMap.entrySet().stream()
                .filter(e -> e.getValue() == maxCount)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }

    // 최다 이모지 슬라이드 리스트
    public List<Integer> findMostReactionSlides(String roomId) {

        String key = "room:" + roomId + ":stickers";

        List<MapRecord<String, Object, Object>> records =
                objectRedisTemplate.opsForStream().range(key, Range.unbounded());

        if (records == null || records.isEmpty()) {
            return List.of();
        }

        Map<Integer, Integer> slideCountMap = new HashMap<>();

        for (MapRecord<String, Object, Object> record : records) {
            Object slideObj = record.getValue().get("slide");
            if (slideObj == null) continue;

            try {
                int slide = Integer.parseInt(slideObj.toString());
                slideCountMap.merge(slide, 1, Integer::sum);
            } catch (NumberFormatException e) {
                log.warn("Invalid slide value in stream: {}", slideObj);
            }
        }

        int maxCount = slideCountMap.values().stream()
                .max(Integer::compare)
                .orElse(0);

        return slideCountMap.entrySet().stream()
                .filter(e -> e.getValue() == maxCount)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }
}
