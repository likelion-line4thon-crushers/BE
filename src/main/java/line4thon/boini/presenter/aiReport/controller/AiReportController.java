package line4thon.boini.presenter.aiReport.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import line4thon.boini.global.common.response.BaseResponse;
import line4thon.boini.presenter.aiReport.dto.response.MostReactionStickerResponse;
import line4thon.boini.presenter.aiReport.dto.response.MostRevisitResponse;
import line4thon.boini.presenter.aiReport.dto.response.ReportResponse;
import line4thon.boini.presenter.aiReport.dto.response.ReportTopResponse;
import line4thon.boini.presenter.aiReport.mapper.ReportMapper;
import line4thon.boini.presenter.aiReport.service.AiReportService;
import line4thon.boini.presenter.aiReport.service.ReportService;
import line4thon.boini.presenter.page.dto.response.SlideAudienceCountResponse;
import line4thon.boini.presenter.room.entity.Report;
import line4thon.boini.presenter.room.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

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
    private final ReportRepository reportRepository;
    private final ObjectMapper objectMapper;

    private final ReportService reportService;

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

        // 1️⃣ roomId로 Report 조회
        Optional<Report> optionalReport = reportRepository.findByRoomId(roomId);

        // 2️⃣ 존재할 때만 popularEmoji 값 변경
        optionalReport.ifPresent(report -> {
            try {
                ObjectMapper mapper = new ObjectMapper();
                // list를 JSON 문자열로 변환
                String json = mapper.writeValueAsString(list);
                report.setPopularEmoji(json);
                reportRepository.save(report); // JPA가 더티체킹으로 자동 업데이트
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        return BaseResponse.success(list);
    }

    @Operation(
            summary = "재방문 수 가장 많은 슬라이드 반환",
            description = """
          재방문 수가 가장 많은 슬라이드를 반환합니다.\n
          `slide` : 재방문 수가 가장 많은 슬라이드 수\n
          `totalRevisits` : 해당 슬라이드의 전체 방문 수\n
          `totalAudienceCount` : 전체 청중 수\n
          `uniqueUsers` : 재방문한 청중 수\n
          `multiRevisitUsers` : 2번 이상 재방문한 청중 수
          """
    )
    @GetMapping("/{roomId}/mostRevisit")
    public BaseResponse<MostRevisitResponse> getMostRevisit(@PathVariable String roomId) {
        MostRevisitResponse mostRevisitResponse = aiReportService.getMostRevisit(roomId);

        // 1️⃣ roomId로 Report 조회
        Optional<Report> optionalReport = reportRepository.findByRoomId(roomId);

        optionalReport.ifPresent(report -> {
            try {
                // mostRevisitResponse가 null이면 빈 JSON 배열로 처리
                String revisitJson = (mostRevisitResponse != null)
                        ? objectMapper.writeValueAsString(mostRevisitResponse)
                        : "[]";

                report.setRevisit(revisitJson);
                reportRepository.save(report); // JPA가 더티체킹으로 자동 업데이트
            } catch (JsonProcessingException e) {
                e.printStackTrace();
                // JSON 변환 실패 시 빈 배열로 안전하게 처리
                report.setRevisit("[]");
                reportRepository.save(report);
            }
        });

        return BaseResponse.success(mostRevisitResponse);
    }

    @Operation(
            summary = "총 이미지 반응, 총 실시간 질문 수, 주목해야 할 슬라이드",
            description = """
          AI 리포트 맨 위 부분 반환하는 API 입니다.
          """
    )
    @GetMapping("/{roomId}/getReport/top")
    public BaseResponse<ReportTopResponse> getReportTop(@PathVariable String roomId) {
        ReportTopResponse reportTop = aiReportService.getReportTop(roomId);
        return BaseResponse.success(reportTop);
    }

    @GetMapping("/{roomId}")
    @Operation(summary = "roomId 기준 리포트 조회", description = "특정 roomId의 리포트를 반환합니다.")
    public BaseResponse<ReportResponse> getByRoomId(@PathVariable String roomId) {
        var entity = reportService.getReportByRoomId(roomId);
        return BaseResponse.success(ReportMapper.toDto(entity));
    }

}
