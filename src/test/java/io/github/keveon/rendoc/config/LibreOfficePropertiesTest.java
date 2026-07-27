package io.github.keveon.rendoc.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class LibreOfficePropertiesTest {

    @Test
    void acceptsAValidOfficeProcessConfiguration() {
        var properties = properties(2002, 20);

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(properties)).isEmpty();
        }
    }

    @Test
    void rejectsInvalidPortAndProcessLimits() {
        var properties = properties(80, 0);

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(properties))
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .containsExactlyInAnyOrder("port", "maxTasksPerProcess");
        }
    }

    @Test
    void rejectsNonPositiveTimeouts() {
        var properties = new LibreOfficeProperties(
                null,
                Path.of("build/libreoffice-test"),
                2002,
                20,
                Duration.ZERO,
                Duration.ofSeconds(-1),
                Duration.ofSeconds(120));

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(properties)).isNotEmpty();
        }
    }

    @Test
    void rejectsPositiveDurationsThatTruncateToZeroMilliseconds() {
        var properties = new LibreOfficeProperties(
                null,
                Path.of("build/libreoffice-test"),
                2002,
                20,
                Duration.ofNanos(1),
                Duration.ofSeconds(30),
                Duration.ofSeconds(120));

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(properties)).isNotEmpty();
        }
    }

    private static LibreOfficeProperties properties(int port, int maxTasksPerProcess) {
        return new LibreOfficeProperties(
                null,
                Path.of("build/libreoffice-test"),
                port,
                maxTasksPerProcess,
                Duration.ofSeconds(120),
                Duration.ofSeconds(30),
                Duration.ofSeconds(120));
    }
}
