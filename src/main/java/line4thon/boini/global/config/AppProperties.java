package line4thon.boini.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {
  private Room room = new Room();
  private Jwt jwt = new Jwt();
  private final Urls urls = new Urls();
  private final S3 s3 = new S3();

  @Getter @Setter
  public static class Room {
    private long ttlSeconds = 86400;
  }

  @Getter @Setter
  public static class Jwt {
    private long presenterTtlHours = 2;
  }

  @Getter @Setter
  public static class Urls {
    // 프론트 조인 URL prefix
    private String joinBase = "http://localhost:5173/j/"; // default 값
    // WebSocket 엔드포인트
    private String ws = "ws://localhost:8080/ws"; // default 값
  }

  @Getter @Setter
  public static class S3 {
    private String bucket;
    private String region;
    private String cloudfrontDomain;
    private long presignSeconds = 3600;
    private String rootPrefix = "presentations";
  }
}
