package line4thon.boini.presenter.page.service;

import line4thon.boini.audience.option.dto.response.UnlockResponse;
import line4thon.boini.global.common.exception.CustomException;
import line4thon.boini.global.common.response.BaseResponse;
import line4thon.boini.presenter.aiReport.exception.ReportErrorCode;
import line4thon.boini.presenter.page.dto.request.ChangeAudiencePageRequest;
import line4thon.boini.presenter.page.dto.request.ChangePageRequest;
import line4thon.boini.presenter.page.dto.response.ChangePageResponse;
import line4thon.boini.presenter.page.dto.response.SlideAudienceCountResponse;
import line4thon.boini.presenter.page.exception.PageErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

@Service
@Slf4j
public class PageService {

    private final SimpMessagingTemplate messagingTemplate;
    private final RedisTemplate<String, String> redisTemplate;


    public PageService(SimpMessagingTemplate messagingTemplate,
                          RedisTemplate<String, String> redisTemplate) {
        this.messagingTemplate = messagingTemplate;
        this.redisTemplate = redisTemplate;
    }

    public void updateCurrentPage(String sessionId, ChangePageRequest msg) {
        try{
            // Redis Stream 저장
//            Map<String, String> event = Map.of(
//                    "sessionId", sessionId,
//                    "type", "PRESENTER_PAGE_CHANGE",
//                    "page", String.valueOf(msg.getChangedPage()),
//                    "timestamp", String.valueOf(System.currentTimeMillis())
//            );
//            redisTemplate.opsForStream().add("page_events", event);


            // 모든 구독자에게 브로드캐스트: /topic/presentation/{sessionId}
            messagingTemplate.convertAndSend("/topic/presentation/" + sessionId + "/pageChange", msg);
            log.info("페이지 전환 브로드캐스트 완료: roomId={}, beforePage={}, changedPage={}", sessionId, msg.getBeforePage(), msg.getChangedPage());
            redisTemplate.opsForValue().set("room:" + sessionId + ":presenterPage", msg.getChangedPage().toString());

            String maxSlide = redisTemplate.opsForValue().get("room:" + sessionId + ":maxSlide");

            if(maxSlide==null) maxSlide = "0";

            if(Integer.parseInt(maxSlide) < msg.getChangedPage()){
                redisTemplate.opsForValue().set("room:" + sessionId + ":maxSlide",  msg.getChangedPage().toString());
                String totalPage = redisTemplate.opsForValue().get("room:" + sessionId + ":totalPage");
                String unlock = redisTemplate.opsForValue().get("room:" + sessionId + ":option:slideUnlock");
                String presenterPage = redisTemplate.opsForValue().get("room:"+sessionId+":presenterPage");

                UnlockResponse response = new UnlockResponse().builder()
                        .maxRevealedPage(msg.getChangedPage().toString())
                        .totalPages(totalPage)
                        .revealAllSlides(unlock)
                        .presenterPage(presenterPage)
                        .build();

                messagingTemplate.convertAndSend("/topic/presentation/" + sessionId + "/option/unlock", response);

            }

        } catch (Exception e){
            log.error("페이지 전환 실패: roomId={}, err={}", sessionId, e.toString());
            throw new CustomException(PageErrorCode.NOT_RECEIVE_PAGE_DATA);
        }
    }

    public void updateAudiencePage(String sessionId, ChangeAudiencePageRequest msg) {
        try{
            // Redis Stream 저장
//            Map<String, String> event = Map.of(
//                    "sessionId", sessionId,
//                    "type", "PRESENTER_PAGE_CHANGE",
//                    "page", String.valueOf(msg.getChangedPage()),
//                    "timestamp", String.valueOf(System.currentTimeMillis())
//            );
//            redisTemplate.opsForStream().add("page_events", event);
            //사용자 Redis 현재 위치 수정
            //각 페이지 별로 Key에서 집합 수정
            log.info("페이지 전환 완료: roomId={}, audienceId={}, beforePage={}, changedPage={}", sessionId, msg.getAudienceId(), msg.getBeforePage(), msg.getChangedPage());

            String key1 =  "room:" + sessionId + ":slide:" + msg.getBeforePage();
            String key2 =  "room:" + sessionId + ":slide:" + msg.getChangedPage();

            redisTemplate.opsForSet().remove(key1, msg.getAudienceId());
            redisTemplate.opsForSet().add(key2, msg.getAudienceId());

            String key3 = "room:" + sessionId + ":presenterPage";
            String presenterPage = redisTemplate.opsForValue().get(key3);

            if (presenterPage == null) {
                presenterPage = "0"; // 기본 페이지
            }

            int pageNumber = Integer.parseInt(presenterPage); // Redis 값 -> int
            int changedPage = msg.getChangedPage();           // int로 가져오기

//            log.info("pageNumber={}, changedPage={}", pageNumber, changedPage);

            if(changedPage != pageNumber) {
                String key4 = "room:" + sessionId + ":revisit:" + msg.getChangedPage();
                redisTemplate.opsForValue().increment(key4);
                String key5 = "room:" + sessionId + ":revisit:user:" + msg.getChangedPage() + ":" + msg.getAudienceId();
                redisTemplate.opsForValue().increment(key5);
                String key6 = "room:"+sessionId+":revisit:users:" + msg.getChangedPage();
                redisTemplate.opsForSet().add(key6, msg.getAudienceId());
            }

        } catch (Exception e){
            log.error("페이지 전환 실패: roomId={}, err={}", sessionId, e.toString());
            throw new CustomException(PageErrorCode.CHANGE_PAGE_ERROR);
        }
    }

    public SlideAudienceCountResponse getSlideAudienceCounts(String roomId) {
        String presenterPageKey = "room:" + roomId + ":presenterPage";
//        String audienceOnlineKey = "room:" + roomId + ":audience:online";

        // 발표자가 보고 있는 현재 슬라이드 번호 가져오기
        String presenterPageValue = redisTemplate.opsForValue().get(presenterPageKey);
        if (presenterPageValue == null) {
            throw new IllegalStateException("Presenter page not set for room " + roomId);
        }
        int presenterSlide = Integer.parseInt(presenterPageValue);

        // 온라인 참여자 목록 가져오기
//        Set<String> audienceIds = redisTemplate.opsForSet().members(audienceOnlineKey);
//        if (audienceIds == null || audienceIds.isEmpty()) {
//            return new SlideAudienceCountResponse(0, 0, 0);
//        }

        int front = 0;
        int current = 0;
        int back = 0;

        String totalpage = redisTemplate.opsForValue().get("room:" + roomId + ":totalPage");
        if (totalpage == null) {
            throw new CustomException(ReportErrorCode.TOTAL_PAGE_NULL);
        }
        int slides = Integer.parseInt(totalpage);

//        System.out.println("슬라이드 수 : "+slides);

        for (int slide = 1; slide <= slides; slide++) {

            String audienceSlideKey = "room:" + roomId + ":slide:" + slide;

            Long count = redisTemplate.opsForSet().size(audienceSlideKey);
//            System.out.printf("슬라이드에 있는 청중 수" + count);
            if (count == null) continue;
            else count=count-1;

            if (slide < presenterSlide) front+=count;
            else if (slide == presenterSlide) current+=count;
            else back+=count;
        }

        int total = front + current + back;

        log.info("청중 분포 front={} , current={}, back={}", front, current, back);

        long front_per = Math.round(front * 100.0 / total);
        long current_per = Math.round(current * 100.0 / total);
        long back_per = Math.round(back * 100.0 / total);

        return new SlideAudienceCountResponse(front_per, current_per, back_per);
    }

    // 총 페이지 수 반환
    public int countSlideKeys(String roomId) {
        ScanOptions options = ScanOptions.scanOptions()
                .match("room:" + roomId + ":slide:*")
                .count(1000)
                .build();

        int count = 0;
        Cursor<byte[]> cursor = redisTemplate.getConnectionFactory()
                .getConnection()
                .scan(options);

        while (cursor.hasNext()) {
            cursor.next();
            count++;
        }

        return count;
    }

    public void FocusOn(String sessionId){
        String key =  "room:" + sessionId + ":presenterPage";
        String currentPage = redisTemplate.opsForValue().get(key);
        messagingTemplate.convertAndSend("/topic/presentation/" + sessionId + "/focusOn", currentPage);
    }

}
