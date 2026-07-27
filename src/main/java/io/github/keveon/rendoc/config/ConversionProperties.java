package io.github.keveon.rendoc.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("rendoc.conversion")
public record ConversionProperties(
        @Min(1) @Max(104857600) long maxInputBytes,
        @Min(1) @Max(32) int maxInFlight,
        @NotNull Path workingDirectory) {
}
