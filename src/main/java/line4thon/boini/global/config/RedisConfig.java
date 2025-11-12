package line4thon.boini.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@Slf4j
public class RedisConfig {

  @Bean
  @Primary
  public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
    RedisTemplate<String, String> template = new RedisTemplate<>();
    template.setConnectionFactory(connectionFactory);
    StringRedisSerializer stringSerializer = new StringRedisSerializer();
    template.setKeySerializer(stringSerializer);
    template.setValueSerializer(stringSerializer);
    template.setHashKeySerializer(stringSerializer);
    template.setHashValueSerializer(stringSerializer);
    template.afterPropertiesSet();
    return template;
  }

  // 객체 저장용 RedisTemplate
  @Bean
  public RedisTemplate<String, Object> objectRedisTemplate(
          RedisConnectionFactory connectionFactory,
          ObjectMapper redisObjectMapper) {

    RedisTemplate<String, Object> template = new RedisTemplate<>();
    template.setConnectionFactory(connectionFactory);

    // JSON 직렬화 설정
    GenericJackson2JsonRedisSerializer jsonSerializer =
            new GenericJackson2JsonRedisSerializer(redisObjectMapper);

    // Key는 String, Value는 JSON으로 직렬화
    template.setKeySerializer(new StringRedisSerializer());
    template.setValueSerializer(jsonSerializer);
    template.setHashKeySerializer(new StringRedisSerializer());
    template.setHashValueSerializer(jsonSerializer);

    // 기본 직렬화 방식 설정
    template.setDefaultSerializer(jsonSerializer);

    template.afterPropertiesSet();

    log.info("Object RedisTemplate 설정 완료 (JSON 직렬화)");
    return template;
  }

  // Redis용 ObjectMapper 설정
  @Bean
  public ObjectMapper redisObjectMapper() {
    ObjectMapper mapper = new ObjectMapper();

    // Java 8 시간 타입 지원 모듈 등록
    mapper.registerModule(new JavaTimeModule());

    // 알 수 없는 속성 무시 (역직렬화 시 호환성)
    mapper.configure(
        com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
        false
    );

    log.info("Redis ObjectMapper 설정 완료 (JavaTimeModule 포함)");
    return mapper;
  }
}