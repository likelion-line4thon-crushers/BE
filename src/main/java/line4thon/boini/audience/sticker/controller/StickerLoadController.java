package line4thon.boini.audience.sticker.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import line4thon.boini.audience.sticker.dto.response.StickerLoadResponse;
import line4thon.boini.audience.sticker.dto.response.StickerResponse;
import org.springframework.cglib.core.Local;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/stickers")
@RequiredArgsConstructor
@Tag(name = "Sticker", description = "스티커 관련")
public class StickerLoadController {

    private final RedisTemplate<String, Object> objectRedisTemplate;

    /**
     * 특정 세션의 모든 스티커 조회
     */
    @Operation(
            summary = "발표자용 - 새로고침 시 리액션 스티커들을 전부 가져오는 api",
            description = """
            해당 세션에서 받은 모든 리액션 스티커들을 가져옴
          """
    )
    @GetMapping("/{sessionId}/all")
    public List<StickerLoadResponse> getAllStickers(@PathVariable String sessionId) {
        String key = "room:" + sessionId + ":stickers";

        // Redis Stream 전체 조회
        List<MapRecord<String, Object, Object>> records =
                objectRedisTemplate.opsForStream().range(key, Range.unbounded());

        // Redis 데이터 → DTO 변환
        return Objects.requireNonNull(records).stream()
                .map(record -> {
                    Map<Object, Object> fields = record.getValue();
                    return StickerLoadResponse.builder()
                            .emoji((Integer) fields.get("emoji"))
                            .x(Double.parseDouble(fields.get("x").toString()))
                            .y(Double.parseDouble(fields.get("y").toString()))
                            .slide((Integer) fields.get("slide"))
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * 특정 audienceId의 스티커만 조회
     */
    @Operation(
            summary = "청중용 - 새로고침 시 자신이 붙였던 리액션 스티커들을 전부 가져오는 api",
            description = """
            해당 세션에서 자신이 붙인 모든 리액션 스티커들을 가져옴
          """
    )
    @GetMapping("/{sessionId}/audience/{audienceId}")
    public List<StickerLoadResponse> getStickersByAudience(
            @PathVariable String sessionId,
            @PathVariable String audienceId) {

        String key = "room:" + sessionId + ":stickers";

        // Redis Stream 전체 조회
        List<MapRecord<String, Object, Object>> records =
                objectRedisTemplate.opsForStream().range(key, Range.unbounded());

        // audienceId로 필터링
        return Objects.requireNonNull(records).stream()
                .map(MapRecord::getValue)
                .filter(fields -> audienceId.equals(fields.get("audienceId")))
                .map(fields -> StickerLoadResponse.builder()
                        .emoji((Integer) fields.get("emoji"))
                        .x(Double.parseDouble(fields.get("x").toString()))
                        .y(Double.parseDouble(fields.get("y").toString()))
                        .slide((Integer) fields.get("slide"))
                        .build())
                .collect(Collectors.toList());
    }
}
