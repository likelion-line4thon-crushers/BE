package line4thon.boini.global.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 비동기 처리 설정.
 * @EnableAsync: 프로젝트 전체에서 @Async 어노테이션을 활성화합니다.
 * 연결: PdfParseService.parseAndStream() 에서 "pdfParseExecutor" 빈을 지정해서 사용합니다.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * PDF 페이지 렌더링 전용 스레드풀.
     * - corePoolSize(2): 평시 2개 스레드 유지 → 동시에 2개 PDF 파싱 가능
     * - maxPoolSize(4): 최대 4개까지 확장
     * - queueCapacity(20): 대기열 20개 초과 시 RejectedExecutionException 발생
     * 연결: PdfParseService → @Async("pdfParseExecutor")
     */
    @Bean(name = "pdfParseExecutor")
    public Executor pdfParseExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("pdf-parse-");
        executor.initialize();
        return executor;
    }
}
