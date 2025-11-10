package line4thon.boini.audience.option.service;

import line4thon.boini.audience.option.dto.request.OptionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OptionService {

    private final RedisTemplate<String, String> redisTemplate;



}
