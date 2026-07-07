package com.pulseflow.backend.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "PulseFlow API",
                version = "v1",
                description = "PulseFlow backend API"
        )
)
public class OpenApiConfig {
}

