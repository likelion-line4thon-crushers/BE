package line4thon.boini.audience.question.controller;

import line4thon.boini.global.common.response.BaseResponse;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dev/streams")
public class StreamDebugController {

  private final StringRedisTemplate redis;
  public StreamDebugController(StringRedisTemplate redis) { this.redis = redis; }

  @GetMapping("/{roomId}")
  public BaseResponse<List<MapRecord<String, Object, Object>>> tail(
      @PathVariable String roomId
  ) {
    String streamKey = "stream:question:events:" + roomId;

    // Range<String> 로 지정해야 함
    Range<String> range = Range.closed("0-0", "+");
    var list = redis.opsForStream().range(streamKey, range);

    return BaseResponse.success("스트림 조회 성공", list);
  }
}
