package io.github.keveon.rendoc.conversion;

import io.github.keveon.rendoc.config.ConversionProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ConversionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConversionService.class);
    private static final int PDF_HEADER_LENGTH = 8;
    private static final int PDF_TAIL_LIMIT = 2_048;
    private static final long MINIMUM_PDF_LENGTH = 32;

    private final DocumentEngine engine;
    private final ConversionProperties properties;
    private final Semaphore capacity;

    public ConversionService(DocumentEngine engine, ConversionProperties properties) {
        this.engine = engine;
        this.properties = properties;
        this.capacity = new Semaphore(properties.maxInFlight(), true);
    }

    public ConversionResult convert(InputStream input, long contentLength, SourceFormat source) {
        if (contentLength > properties.maxInputBytes()) {
            throw new ConversionException(ConversionException.Reason.PAYLOAD_TOO_LARGE);
        }
        if (!capacity.tryAcquire()) {
            throw new ConversionException(ConversionException.Reason.CAPACITY_EXCEEDED);
        }

        Path requestDirectory = null;
        long startedAt = System.nanoTime();
        try {
            Files.createDirectories(properties.workingDirectory());
            requestDirectory = Files.createTempDirectory(properties.workingDirectory(), "request-");
            Path sourceFile = requestDirectory.resolve("input." + source.extension());
            Path outputFile = requestDirectory.resolve("output.pdf");
            long inputBytes = BoundedInput.copy(input, sourceFile, properties.maxInputBytes());
            if (inputBytes == 0) {
                throw new ConversionException(ConversionException.Reason.INVALID_DOCUMENT);
            }

            engine.convert(sourceFile, outputFile, source);
            verifyPdf(outputFile);
            LOGGER.info(
                    "Document conversion completed: source={}, inputBytes={}, durationMs={}",
                    source.name().toLowerCase(Locale.ROOT),
                    inputBytes,
                    (System.nanoTime() - startedAt) / 1_000_000);
            return ConversionResult.owned(outputFile, requestDirectory);
        } catch (ConversionException exception) {
            cleanFailedRequest(requestDirectory, exception);
            throw exception;
        } catch (Exception exception) {
            var wrapped = new ConversionException(ConversionException.Reason.INTERNAL_ERROR, exception);
            cleanFailedRequest(requestDirectory, wrapped);
            throw wrapped;
        } finally {
            capacity.release();
        }
    }

    private static void verifyPdf(Path output) throws IOException {
        if (!Files.isRegularFile(output) || Files.size(output) < MINIMUM_PDF_LENGTH) {
            throw invalidPdf();
        }

        try (var pdf = new RandomAccessFile(output.toFile(), "r")) {
            byte[] header = new byte[PDF_HEADER_LENGTH];
            pdf.readFully(header);
            String version = new String(header, StandardCharsets.US_ASCII);
            if (!version.matches("%PDF-[12]\\.[0-9]")) {
                throw invalidPdf();
            }

            long tailStart = Math.max(0, pdf.length() - PDF_TAIL_LIMIT);
            pdf.seek(tailStart);
            byte[] tail = new byte[(int) (pdf.length() - tailStart)];
            pdf.readFully(tail);
            String trailer = new String(tail, StandardCharsets.ISO_8859_1);
            int endOfFile = trailer.lastIndexOf("%%EOF");
            if (endOfFile < 0 || trailer.lastIndexOf("startxref", endOfFile) < 0) {
                throw invalidPdf();
            }
        }
    }

    private static ConversionException invalidPdf() {
        return new ConversionException(ConversionException.Reason.INVALID_DOCUMENT);
    }

    private static void cleanFailedRequest(Path directory, ConversionException originalFailure) {
        if (directory == null) {
            return;
        }
        try {
            TemporaryFiles.deleteRecursively(directory);
        } catch (IOException cleanupFailure) {
            originalFailure.addSuppressed(cleanupFailure);
            TemporaryFiles.scheduleDeletionOnExit(directory);
            LOGGER.error("Conversion workspace cleanup failed; deletion is scheduled for process exit", cleanupFailure);
        }
    }
}
