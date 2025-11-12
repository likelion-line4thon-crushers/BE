package line4thon.boini.audience.liveFeedback.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import line4thon.boini.audience.liveFeedback.dto.response.LiveFeedbackStateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LiveFeedbackService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public void initFeedbackHashes(String sessionId, int slides) {
        for (int slide = 1; slide <= slides; slide++) {
            String key = String.format("room:%s:liveFeedback:slide:%d", sessionId, slide);

            redisTemplate.opsForHash().put(key, "status", "NONE");
            redisTemplate.opsForHash().put(key, "message", "반응 분석 중...");
            redisTemplate.opsForHash().put(key, "mostPeopleCounts", "0");
        }
    }

}
