package com.insurance.platform.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Java 公共 OpenAPI 配置挂载点。
 *
 * <p>只把 Java 公共业务路径纳入 Web Client 契约。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI insurancePlatformOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Insurance AI Platform API")
                .version("v1")
                .description("Java Backend public API"));
    }

    @Bean
    public GroupedOpenApi publicV1OpenApi() {
        return GroupedOpenApi.builder()
                .group("public-v1")
                .pathsToMatch("/api/v1/**")
                .build();
    }
}
