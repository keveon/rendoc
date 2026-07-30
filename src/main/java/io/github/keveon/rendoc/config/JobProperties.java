package io.github.keveon.rendoc.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("rendoc.jobs")
public record JobProperties(
        boolean enabled,
        @Min(1) @Max(1000) int maxBatchSize,
        @NotNull Duration pollInterval,
        @NotNull Duration retention,
        @NotNull Duration cleanupInterval,
        @NotNull Path outputDir) {
}
