package line4thon.boini.audience.room.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import line4thon.boini.audience.feedback.dto.request.CreateFeedbackRequest;
import line4thon.boini.audience.feedback.dto.response.CreateFeedbackResponse;
import line4thon.boini.audience.room.dto.response.RoomInfoResponse;
import line4thon.boini.audience.room.service.AudienceAuthService;
import line4thon.boini.audience.room.service.RoomInfoService;
import line4thon.boini.global.common.response.BaseResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/roomAudience/")
public class RoomAudienceController {
    private final RoomInfoService roomInfoService;

    @PostMapping("/rooms/{roomId}/info")
    @Operation(
            summary = "현재 방 정보 반환",
            description = """
          현재 방 정보들 반환
          """
    )
    public BaseResponse<RoomInfoResponse> roomInfo(
            @PathVariable("roomId") String roomId
    ) {
        return roomInfoService.roomInfo(roomId);
    }
}
