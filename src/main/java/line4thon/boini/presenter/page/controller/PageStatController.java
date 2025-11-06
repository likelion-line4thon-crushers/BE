package line4thon.boini.presenter.page.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import line4thon.boini.presenter.page.dto.response.SlideAudienceCountResponse;
import line4thon.boini.presenter.page.service.PageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/pages")
@RequiredArgsConstructor
@Tag(name = "Page", description = "페이지 관련")
public class PageStatController {

    private final PageService pageService;

    @Operation(
            summary = "현재 청중 분포",
            description = """
          현재 청중 분포수 반환
          """
    )
    @GetMapping("/{roomId}/audience-slide-stats")
    public SlideAudienceCountResponse getAudienceSlideStats(@PathVariable String roomId) {
        return pageService.getSlideAudienceCounts(roomId);
    }
}
