package line4thon.boini.audience.question.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import line4thon.boini.global.common.response.BaseResponse;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dev/streams")
@Tag(name = "Stream Debug", description = "Redis Stream 디버그 및 조회용 API (개발용)")
public class StreamDebugController {

  private final StringRedisTemplate redis;
  public StreamDebugController(StringRedisTemplate redis) { this.redis = redis; }

  @GetMapping("/{roomId}")
  @Operation(
      summary = "Redis Stream 조회 (roomId별)",
      description = """
          특정 방(roomId)에 해당하는 Redis Stream(`stream:question:events:{roomId}`)의 전체 이벤트를 조회합니다.
          <br><br>
          개발 및 디버그용 API로, 운영 환경에서는 호출하지 마세요.
          """
  )
  public BaseResponse<List<MapRecord<String, Object, Object>>> tail(
      @PathVariable String roomId
  ) {
    String streamKey = "stream:question:events:" + roomId;

    Range<String> range = Range.closed("0-0", "+");
    var list = redis.opsForStream().range(streamKey, range);

    return BaseResponse.success("스트림 조회 성공", list);
  }
}
