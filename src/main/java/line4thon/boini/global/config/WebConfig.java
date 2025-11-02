package line4thon.boini.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class WebConfig implements WebMvcConfigurer {
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // favicon 요청 무시
        registry.addResourceHandler("/favicon.ico")
                .addResourceLocations("classpath:/static/"); // 빈 파일만 있어도 됨
    }
}
