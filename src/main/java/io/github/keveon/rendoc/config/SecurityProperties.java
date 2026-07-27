package io.github.keveon.rendoc.config;

import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("rendoc.security")
public record SecurityProperties(
        @Pattern(regexp = "\\S{16,512}", message = "API token must contain 16-512 non-whitespace characters")
        String apiToken) {}
