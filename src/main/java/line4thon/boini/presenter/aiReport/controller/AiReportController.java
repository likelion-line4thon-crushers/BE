package line4thon.boini.presenter.aiReport.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import line4thon.boini.global.common.response.BaseResponse;
import line4thon.boini.presenter.aiReport.dto.response.MostReactionStickerResponse;
import line4thon.boini.presenter.aiReport.dto.response.MostRevisitResponse;
import line4thon.boini.presenter.aiReport.service.AiReportService;
import line4thon.boini.presenter.page.dto.response.SlideAudienceCountResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/aiReport")
@RequiredArgsConstructor
@Tag(name = "AiReport", description = "AI리포트 관련")
public class AiReportController {

    private final SimpMessagingTemplate messagingTemplate;
    private final RedisTemplate<String, String> redisTemplate;
    private final RedisTemplate<String, Object> objectRedisTemplate;
    private final AiReportService aiReportService;

    @Operation(
            summary = "리액션 스티커를 가장 많이 받은 슬라이드 반환",
            description = """
          각 리액션 스티커 별로 가장 많이 받은 슬라이드 반환\n
          만약 이모지가 단 한 번도 쓰이지 않았다면, 해당 이모지의 JSON은 반환X\n
          두 번째로 이모지를 많이 받은 슬라이드가 존재하지 않을 경우 -1로 반환
          """
    )
    @GetMapping("/{roomId}/mostReactionSticker")
    public BaseResponse<List<MostReactionStickerResponse>> getMostReactionSticker(@PathVariable String roomId) {
        List<MostReactionStickerResponse> list = aiReportService.getMostReactionSticker(roomId);

        return BaseResponse.success(list);
    }

    @Operation(
            summary = "재방문 수 가장 많은 슬라이드 반환",
            description = """
          재방문 수가 가장 많은 슬라이드를 반환합니다.\n
          `slide` : 재방문 수가 가장 많은 슬라이드 수\n
          `totalRevisits` : 해당 슬라이드의 전체 재방문자 수\n
          `totalAudienceCount` : 전체 청중 수\n
          `uniqueUsers` : 재방문한 청중 수\n
          `multiRevisitUsers` : 2번 이상 재방문한 청중 수
          """
    )
    @GetMapping("/{roomId}/mostRevisit")
    public BaseResponse<MostRevisitResponse> getMostRevisit(@PathVariable String roomId) {
        MostRevisitResponse mostRevisitResponse = aiReportService.getMostRevisit(roomId);

        return BaseResponse.success(mostRevisitResponse);
    }


}
