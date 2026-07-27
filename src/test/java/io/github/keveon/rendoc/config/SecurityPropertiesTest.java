package io.github.keveon.rendoc.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

class SecurityPropertiesTest {

    @Test
    void requiresASecretWithAtLeastSixteenCharacters() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();

            assertThat(validator.validate(new SecurityProperties("0123456789abcdef"))).isEmpty();
            assertThat(validator.validate(new SecurityProperties("too-short")))
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .contains("apiToken");
            assertThat(validator.validate(new SecurityProperties("token with whitespace")))
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .contains("apiToken");
        }
    }
}
