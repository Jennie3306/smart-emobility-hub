package com.hcmut.emobility.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI apiInfo() {
        return new OpenAPI().info(new Info()
                .title("Smart E-Mobility Hub API")
                .version("v1")
                .description("BTL Cong nghe Phan mem HK261 - API cua 8 module M1..M8"));
    }
}
