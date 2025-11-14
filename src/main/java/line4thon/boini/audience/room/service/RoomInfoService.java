package line4thon.boini.audience.room.service;

import line4thon.boini.audience.room.dto.response.RoomInfoResponse;
import line4thon.boini.global.common.response.BaseResponse;
import line4thon.boini.presenter.room.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RoomInfoService {

    private final RoomService roomService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    public BaseResponse<RoomInfoResponse> roomInfo(String roomId) {
        String key5 = "room:" + roomId + ":presenterPage";
        String presenterPage = redisTemplate.opsForValue().get(key5);

        String maxPage = redisTemplate.opsForValue().get("room:" + roomId + ":maxSlide");
        String sticker = redisTemplate.opsForValue().get("room:" + roomId + ":option:sticker");
        String question = redisTemplate.opsForValue().get("room:" + roomId + ":option:question");
        String feedback = redisTemplate.opsForValue().get("room:" + roomId + ":option:feedback");
        String slideUnlock = redisTemplate.opsForValue().get("room:" + roomId + ":option:slideUnlock");

        String sessionStatus = roomService.getSessionStatus(roomId).name();

        return BaseResponse.success(new RoomInfoResponse(
                roomId,
                presenterPage,
                sessionStatus,
                maxPage,
                sticker,
                question,
                feedback,
                slideUnlock
        ));


    }
}
