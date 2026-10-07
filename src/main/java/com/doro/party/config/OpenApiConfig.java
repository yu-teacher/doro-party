package com.doro.party.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI partyOpenAPI() {
        String securitySchemeName = "BearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("DORO Party Service API")
                        .description("DORO 플랫폼 기반 Velog 스타일의 오픈 기술 블로그 & 출간 퍼블리싱 마이크로서비스 API")
                        .version("v1.0.0"))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }
}
