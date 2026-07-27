package io.github.keveon.rendoc.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("rendoc.libreoffice")
public record LibreOfficeProperties(
        String officeHome,
        @NotNull Path workingDirectory,
        @Min(1024) @Max(65535) int port,
        @Min(1) @Max(1000) int maxTasksPerProcess,
        @NotNull Duration processTimeout,
        @NotNull Duration taskQueueTimeout,
        @NotNull Duration taskExecutionTimeout) {

    @AssertTrue(message = "LibreOffice timeouts must be positive")
    public boolean hasPositiveTimeouts() {
        return isPositive(processTimeout)
                && isPositive(taskQueueTimeout)
                && isPositive(taskExecutionTimeout);
    }

    private static boolean isPositive(Duration value) {
        return value == null || value.toMillis() > 0;
    }
}
