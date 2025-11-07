package line4thon.boini.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityRequirement;

@Configuration
public class SwaggerConfig {
  @Value("${server.servlet.context-path:}")
  private String contextPath;

  @Bean
  public OpenAPI custonOpenAPI() {
    Server localServer = new Server();
    localServer.setUrl(contextPath);
    localServer.setDescription("Boini Server");

    // BearerAuth Security Scheme 정의
    SecurityScheme bearerAuth = new SecurityScheme()
            .type(SecurityScheme.Type.HTTP)
            .scheme("bearer")
            .bearerFormat("JWT")
            .in(SecurityScheme.In.HEADER)
            .name("Authorization");


    return new OpenAPI()
        .addServersItem(localServer)
        .components(new Components().addSecuritySchemes("BearerAuth", bearerAuth))
        .addSecurityItem(new SecurityRequirement().addList("BearerAuth"))
        .info((new Info()
            .title("Swagger API 명세서")
            .version("1.0")
            .description("Boini Swagger")));
  }

  @Bean
  public GroupedOpenApi customGroupedOpenApi() {
    return GroupedOpenApi.builder().group("api").pathsToMatch("/**").build();
  }
}