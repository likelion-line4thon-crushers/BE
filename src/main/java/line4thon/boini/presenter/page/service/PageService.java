package line4thon.boini.presenter.page.service;

import line4thon.boini.audience.option.dto.response.UnlockResponse;
import line4thon.boini.global.common.exception.CustomException;
import line4thon.boini.presenter.aiReport.exception.ReportErrorCode;
import line4thon.boini.presenter.page.dto.request.ChangeAudiencePageRequest;
import line4thon.boini.presenter.page.dto.request.ChangePageRequest;
import line4thon.boini.presenter.page.dto.response.SlideAudienceCountResponse;
import line4thon.boini.presenter.page.exception.PageErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;


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
            log.info("페이지 전환 완료: roomId={}, audienceId={}, beforePage={}, changedPage={}", sessionId, msg.getAudienceId(), msg.getBeforePage(), msg.getChangedPage());

            String key1 =  "room:" + sessionId + ":slide:" + msg.getBeforePage();
            String key2 =  "room:" + sessionId + ":slide:" + msg.getChangedPage();

            redisTemplate.opsForSet().remove(key1, msg.getAudienceId());
            redisTemplate.opsForSet().add(key2, msg.getAudienceId());

            String key3 = "room:" + sessionId + ":presenterPage";
            String presenterPage = redisTemplate.opsForValue().get(key3);

            if (presenterPage == null) {
                presenterPage = "0";
            }

            int pageNumber = Integer.parseInt(presenterPage);
            int changedPage = msg.getChangedPage();


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

        String presenterPageValue = redisTemplate.opsForValue().get(presenterPageKey);
        if (presenterPageValue == null) {
            throw new IllegalStateException("Presenter page not set for room " + roomId);
        }
        int presenterSlide = Integer.parseInt(presenterPageValue);

        int front = 0;
        int current = 0;
        int back = 0;

        String totalpage = redisTemplate.opsForValue().get("room:" + roomId + ":totalPage");
        if (totalpage == null) {
            throw new CustomException(ReportErrorCode.TOTAL_PAGE_NULL);
        }
        int slides = Integer.parseInt(totalpage);

        for (int slide = 1; slide <= slides; slide++) {

            String audienceSlideKey = "room:" + roomId + ":slide:" + slide;

            Long count = redisTemplate.opsForSet().size(audienceSlideKey);
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
