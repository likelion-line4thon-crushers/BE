package line4thon.boini.presenter.aiReport.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import line4thon.boini.audience.feedback.exception.FeedbackErrorCode;
import line4thon.boini.global.common.exception.CustomException;
import line4thon.boini.presenter.aiReport.dto.response.MostReactionStickerResponse;
import line4thon.boini.presenter.aiReport.dto.response.MostRevisitResponse;
import line4thon.boini.presenter.aiReport.dto.response.ReportTopResponse;
import line4thon.boini.presenter.aiReport.dto.response.RevisitResponse;
import line4thon.boini.presenter.aiReport.exception.ReportErrorCode;
import line4thon.boini.presenter.page.service.PageService;
import line4thon.boini.presenter.room.entity.Report;
import line4thon.boini.presenter.room.repository.ReportRepository;
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
    private final ReportRepository reportRepository;


    public List<MostReactionStickerResponse> getMostReactionSticker(String roomId) {
        String key = "room:" + roomId + ":stickers";

        log.info("=== getMostReactionSticker 시작 ===");
        log.info("Redis Stream Key: {}", key);

        List<MapRecord<String, Object, Object>> records =
                objectRedisTemplate.opsForStream().range(key, Range.unbounded());

        log.info("조회된 Stream 레코드 개수: {}", records.size());

        Map<Integer, Map<Integer, Long>> countMap = new HashMap<>();

        for (MapRecord<String, Object, Object> record : records) {
            log.info("Raw record value: {}", record.getValue());

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

        RevisitResponse most = revisitList.stream()
                .max(Comparator.comparingInt(RevisitResponse::getRevisits))
                .orElse(new RevisitResponse(0, 0));

        int slide = most.getSlide();

        String usersKey = "room:" + roomId + ":revisit:users:" + slide;

        Long uniqueUsers = redisTemplate.opsForSet().size(usersKey);

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
        String countStr = redisTemplate.opsForValue().get(key4);
        int totalAudienceCount = 0;

        if (countStr != null && !countStr.isEmpty()) {
            totalAudienceCount = Integer.parseInt(countStr);
        }


        return MostRevisitResponse.builder()
                .slide(slide)
                .totalRevisits(most.getRevisits())
                .totalAudienceCount(totalAudienceCount)
                .uniqueUsers(uniqueUsers != null ? uniqueUsers.intValue() : 0)
                .multiRevisitUsers(multiRevisitUsers)
                .build();
    }


    public List<RevisitResponse> getRevisit(String roomId) {

        String totalpage = redisTemplate.opsForValue().get("room:" + roomId + ":totalPage");
        if (totalpage == null) {
            throw new CustomException(ReportErrorCode.TOTAL_PAGE_NULL);
        }
        int slides = Integer.parseInt(totalpage);


        List<RevisitResponse> revisitList = new ArrayList<>();

        for (int slide = 1; slide <= slides; slide++) {
            String key = "room:" + roomId + ":revisit:" + slide;

            String revisitStr = redisTemplate.opsForValue().get(key);

            int revisits = (revisitStr != null) ? Integer.parseInt(revisitStr) : 0;

            revisitList.add(RevisitResponse.builder()
                    .slide(slide)
                    .revisits(revisits)
                    .build());
        }

        return revisitList;
    }

    public ReportTopResponse getReportTop(String roomId) {

        Optional<Report> optionalReport = reportRepository.findByRoomId(roomId);

        String key1 = "room:" + roomId + ":stickers";
        String key2 = "room:" + roomId + ":questionCount";

        Long emoji = objectRedisTemplate.opsForStream().size(key1);
        System.out.println("emoji Stream size = " + emoji);

        String question = redisTemplate.opsForValue().get(key2);
        System.out.println("question Stream size = " + question);

        Long focusSlide = Long.valueOf(getFocusSlide(roomId));

        optionalReport.ifPresent(report -> {
            report.setEmojiCount(safeParse(emoji, 0));
            report.setQuestionCount(safeParse(question, 0));
            report.setAttentionSlide(safeParse(focusSlide, 0));
            reportRepository.save(report);
        });

        return ReportTopResponse.builder()
                .totalEmoji(emoji)
                .totalQuestion((question != null) ? Long.parseLong(question) : 0L)
                .focusSlide(focusSlide)
                .build();
    }

    private int safeParse(Object value, int defaultValue) {
        try {
            if (value == null) return defaultValue;
            String str = String.valueOf(value).trim();
            if (str.isEmpty() || str.equalsIgnoreCase("null")) return defaultValue;
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }


    public int getFocusSlide(String roomId) {

        String totalpage = redisTemplate.opsForValue().get("room:" + roomId + ":totalPage");
        if (totalpage == null) {
            throw new CustomException(ReportErrorCode.TOTAL_PAGE_NULL);
        }
        int slides = Integer.parseInt(totalpage);

        int[] questionScores = new int[slides];

        List<Long> slidesWithMostQuestions = getSlidesWithMostQuestions(roomId);

        for (Long slideNum : slidesWithMostQuestions) {
            if (slideNum > 0 && slideNum <= slides) {
                questionScores[slideNum.intValue() - 1] += 5;
            }
        }

        log.info("최다 질문 슬라이드 : roomId={}, slide={}", roomId, slidesWithMostQuestions);

        List<RevisitResponse> revisitList = getRevisit(roomId);

        int maxRevisits = revisitList.stream()
                .mapToInt(RevisitResponse::getRevisits)
                .max()
                .orElse(0);

        if (maxRevisits > 0) {
            List<Integer> mostRevisitedSlides = revisitList.stream()
                    .filter(r -> r.getRevisits() == maxRevisits)
                    .map(RevisitResponse::getSlide)
                    .sorted()
                    .toList();

            for (Integer slideNum : mostRevisitedSlides) {
                if (slideNum > 0 && slideNum <= slides) {
                    questionScores[slideNum - 1] += 4;
                }
            }
            log.info("최다 방문수 슬라이드 : roomId={}, slide={}", roomId, mostRevisitedSlides);
        }



        List<Integer> mostReactionSlides = findMostReactionSlides(roomId);

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

        return maxIndex + 1;
    }

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
                continue;
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
