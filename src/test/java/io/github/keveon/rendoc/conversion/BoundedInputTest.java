package io.github.keveon.rendoc.conversion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BoundedInputTest {

    @TempDir
    Path tempDirectory;

    @Test
    void copiesAStreamAtTheConfiguredLimit() throws Exception {
        var target = tempDirectory.resolve("input.docx");
        var input = new byte[] {1, 2, 3, 4};

        long copied = BoundedInput.copy(new ByteArrayInputStream(input), target, input.length);

        assertThat(copied).isEqualTo(input.length);
        assertThat(Files.readAllBytes(target)).containsExactly(input);
    }

    @Test
    void rejectsAStreamThatExceedsTheConfiguredLimit() {
        var target = tempDirectory.resolve("input.docx");
        var input = new byte[] {1, 2, 3, 4, 5};

        assertThatThrownBy(() -> BoundedInput.copy(new ByteArrayInputStream(input), target, 4))
                .isInstanceOf(ConversionException.class)
                .extracting(error -> ((ConversionException) error).reason())
                .isEqualTo(ConversionException.Reason.PAYLOAD_TOO_LARGE);
    }
}
