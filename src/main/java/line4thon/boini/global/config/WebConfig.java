package line4thon.boini.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

@Configuration
class WebConfig implements WebMvcConfigurer {
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // favicon 요청 무시
        registry.addResourceHandler("/favicon.ico")
                .addResourceLocations("classpath:/static/"); // 빈 파일만 있어도 됨

        // 정적 리소스 설정, /api/** 요청 제외
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        // /api/로 시작하면 정적 리소스로 처리하지 않음
                        if (resourcePath.startsWith("api/**")) {
                            return null;
                        }
                        return super.getResource(resourcePath, location);
                    }
                });
    }
}
