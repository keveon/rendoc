package io.github.keveon.rendoc.conversion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.keveon.rendoc.config.ConversionProperties;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConversionServiceTest {

    private static final byte[] MINIMAL_PDF = """
            %PDF-1.7
            1 0 obj
            << /Type /Catalog >>
            endobj
            xref
            0 2
            0000000000 65535 f
            0000000009 00000 n
            trailer
            << /Size 2 /Root 1 0 R >>
            startxref
            45
            %%EOF
            """.getBytes(StandardCharsets.US_ASCII);

    @TempDir
    Path tempDirectory;

    @Test
    void convertsAValidBoundedInputAndCleansTemporaryFiles() throws Exception {
        var engineCalls = new AtomicInteger();
        DocumentEngine engine = (input, output, source) -> {
            engineCalls.incrementAndGet();
            assertThat(source).isEqualTo(SourceFormat.DOCX);
            assertThat(Files.readString(input)).isEqualTo("document");
            Files.write(output, MINIMAL_PDF);
        };
        var service = service(engine, 1024, 1);

        var result = service.convert(
                new ByteArrayInputStream("document".getBytes(StandardCharsets.UTF_8)),
                8,
                SourceFormat.DOCX);
        var resultPath = result.path();
        var output = new ByteArrayOutputStream();
        result.transferTo(output);
        result.close();

        assertThat(engineCalls).hasValue(1);
        assertThat(output.toString(StandardCharsets.US_ASCII)).startsWith("%PDF-");
        assertThat(resultPath).doesNotExist();
        assertThat(resultPath.getParent()).doesNotExist();
    }

    @Test
    void rejectsContentLengthAboveTheLimitBeforeReadingTheBody() {
        var engineCalls = new AtomicInteger();
        DocumentEngine engine = (input, output, source) -> engineCalls.incrementAndGet();
        var service = service(engine, 4, 1);

        assertThatThrownBy(() -> service.convert(
                        new ByteArrayInputStream(new byte[0]), 5, SourceFormat.DOCX))
                .isInstanceOf(ConversionException.class)
                .extracting(error -> ((ConversionException) error).reason())
                .isEqualTo(ConversionException.Reason.PAYLOAD_TOO_LARGE);
        assertThat(engineCalls).hasValue(0);
    }

    @Test
    void rejectsChunkedBodiesThatCrossTheLimit() {
        DocumentEngine engine = (input, output, source) -> {
            throw new AssertionError("转换引擎不应被调用");
        };
        var service = service(engine, 4, 1);

        assertThatThrownBy(() -> service.convert(
                        new ByteArrayInputStream(new byte[] {1, 2, 3, 4, 5}),
                        -1,
                        SourceFormat.XLSX))
                .isInstanceOf(ConversionException.class)
                .extracting(error -> ((ConversionException) error).reason())
                .isEqualTo(ConversionException.Reason.PAYLOAD_TOO_LARGE);
    }

    @Test
    void rejectsWorkWhenAllConversionSlotsAreOccupied() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        DocumentEngine engine = (input, output, source) -> {
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("测试等待超时");
            }
            Files.write(output, MINIMAL_PDF);
        };
        var service = service(engine, 1024, 1);

        try (var executor = Executors.newSingleThreadExecutor()) {
            var first = executor.submit(() -> service.convert(
                    new ByteArrayInputStream(new byte[] {1}), 1, SourceFormat.DOCX));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> service.convert(
                            new ByteArrayInputStream(new byte[] {2}), 1, SourceFormat.DOCX))
                    .isInstanceOf(ConversionException.class)
                    .extracting(error -> ((ConversionException) error).reason())
                    .isEqualTo(ConversionException.Reason.CAPACITY_EXCEEDED);

            release.countDown();
            first.get(5, TimeUnit.SECONDS).close();
        }
    }

    @Test
    void rejectsAnEngineResultThatIsNotAPdf() {
        DocumentEngine engine = (input, output, source) -> Files.writeString(output, "not-pdf");
        var service = service(engine, 1024, 1);

        assertThatThrownBy(() -> service.convert(
                        new ByteArrayInputStream(new byte[] {1}), 1, SourceFormat.DOCX))
                .isInstanceOf(ConversionException.class)
                .extracting(error -> ((ConversionException) error).reason())
                .isEqualTo(ConversionException.Reason.INVALID_DOCUMENT);
    }

    @Test
    void rejectsATruncatedPdfThatOnlyHasAHeader() {
        DocumentEngine engine = (input, output, source) ->
                Files.writeString(output, "%PDF-1.7\ntruncated", StandardCharsets.US_ASCII);
        var service = service(engine, 1024, 1);

        assertThatThrownBy(() -> service.convert(
                        new ByteArrayInputStream(new byte[] {1}), 1, SourceFormat.DOCX))
                .isInstanceOf(ConversionException.class)
                .extracting(error -> ((ConversionException) error).reason())
                .isEqualTo(ConversionException.Reason.INVALID_DOCUMENT);
    }

    private ConversionService service(DocumentEngine engine, long maxBytes, int maxInFlight) {
        var properties = new ConversionProperties(maxBytes, maxInFlight, tempDirectory);
        return new ConversionService(engine, properties);
    }
}
