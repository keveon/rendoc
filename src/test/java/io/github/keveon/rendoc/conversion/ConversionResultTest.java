package io.github.keveon.rendoc.conversion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConversionResultTest {

    @TempDir
    Path tempDirectory;

    @Test
    void permitsCleanupToBeRetriedAfterADeletionFailure() throws Exception {
        var requestDirectory = Files.createDirectory(tempDirectory.resolve("request"));
        var output = requestDirectory.resolve("output.pdf");
        Files.writeString(output, "%PDF-1.7");
        var attempts = new AtomicInteger();
        var result = ConversionResult.owned(output, requestDirectory, directory -> {
            if (attempts.incrementAndGet() == 1) {
                throw new IOException("simulated cleanup failure");
            }
            Files.delete(output);
            Files.delete(directory);
        });

        assertThatThrownBy(result::close).isInstanceOf(IOException.class);
        assertThat(requestDirectory).exists();

        result.close();

        assertThat(attempts).hasValue(2);
        assertThat(requestDirectory).doesNotExist();
    }

    @Test
    void deletingAnAlreadyMissingWorkspaceIsIdempotent() {
        Path missing = tempDirectory.resolve("already-removed");

        assertThatCode(() -> TemporaryFiles.deleteRecursively(missing)).doesNotThrowAnyException();
    }
}
