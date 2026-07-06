package line4thon.boini.presenter.aiReport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import line4thon.boini.presenter.page.service.PageService;
import line4thon.boini.presenter.room.repository.ReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class AiReportServiceTest {

    private RedisTemplate<String, String> redisTemplate;
    private RedisTemplate<String, Object> objectRedisTemplate;
    private ValueOperations<String, String> valueOps;
    private SetOperations<String, String> setOps;
    private AiReportService service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redisTemplate = mock(RedisTemplate.class);
        objectRedisTemplate = mock(RedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        setOps = mock(SetOperations.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.opsForSet()).thenReturn(setOps);

        service = new AiReportService(
                redisTemplate,
                objectRedisTemplate,
                new ObjectMapper(),
                mock(PageService.class),
                mock(StringRedisTemplate.class),
                mock(ReportRepository.class)
        );
    }

    @Test
    void getMostRevisitReturnsTopFiveRankedByUniqueUsersThenTotalRevisits() {
        when(valueOps.get("room:room-1:totalPage")).thenReturn("4");
        when(valueOps.get("room:room-1:revisit:1")).thenReturn("10");
        when(valueOps.get("room:room-1:revisit:2")).thenReturn("5");
        when(valueOps.get("room:room-1:revisit:3")).thenReturn("7");
        when(valueOps.get("room:room-1:revisit:4")).thenReturn("0");
        when(valueOps.get("room:room-1:enterAudienceCount")).thenReturn("9");

        when(setOps.members("room:room-1:revisit:users:1")).thenReturn(Set.of("a1"));
        when(setOps.members("room:room-1:revisit:users:2")).thenReturn(Set.of("b1", "b2"));
        when(setOps.members("room:room-1:revisit:users:3")).thenReturn(Set.of("c1", "c2"));
        when(setOps.members("room:room-1:revisit:users:4")).thenReturn(Set.of());

        when(valueOps.get("room:room-1:revisit:user:1:a1")).thenReturn("10");
        when(valueOps.get("room:room-1:revisit:user:2:b1")).thenReturn("1");
        when(valueOps.get("room:room-1:revisit:user:2:b2")).thenReturn("1");
        when(valueOps.get("room:room-1:revisit:user:3:c1")).thenReturn("2");
        when(valueOps.get("room:room-1:revisit:user:3:c2")).thenReturn("1");

        var response = service.getMostRevisit("room-1");

        assertThat(response.getSlide()).isEqualTo(3);
        assertThat(response.getUniqueUsers()).isEqualTo(2);
        assertThat(response.getTotalRevisits()).isEqualTo(7);
        assertThat(response.getTotalAudienceCount()).isEqualTo(9);
        assertThat(response.getMultiRevisitUsers()).isEqualTo(1);

        assertThat(response.getTop5()).hasSize(3);
        assertThat(response.getTop5())
                .extracting("slideNumber")
                .containsExactly(3, 2, 1);
    }

    @Test
    void getMostRevisitReturnsEmptyTopFiveWhenThereIsNoRevisitActivity() {
        when(valueOps.get("room:room-empty:totalPage")).thenReturn("2");
        when(valueOps.get("room:room-empty:revisit:1")).thenReturn("0");
        when(valueOps.get("room:room-empty:revisit:2")).thenReturn("0");
        when(valueOps.get("room:room-empty:enterAudienceCount")).thenReturn("3");
        when(setOps.members("room:room-empty:revisit:users:1")).thenReturn(Set.of());
        when(setOps.members("room:room-empty:revisit:users:2")).thenReturn(Set.of());

        var response = service.getMostRevisit("room-empty");

        assertThat(response.getSlide()).isZero();
        assertThat(response.getUniqueUsers()).isZero();
        assertThat(response.getTotalRevisits()).isZero();
        assertThat(response.getTotalAudienceCount()).isEqualTo(3);
        assertThat(response.getTop5()).isEmpty();
    }

    @Test
    void getTopRevisitSlidesReturnsEmptyListWhenLimitIsNotPositive() {
        assertThat(service.getTopRevisitSlides("room-1", 0)).isEmpty();
        assertThat(service.getTopRevisitSlides("room-1", -1)).isEmpty();
    }
}
