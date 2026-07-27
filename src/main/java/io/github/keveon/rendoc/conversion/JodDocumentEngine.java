package io.github.keveon.rendoc.conversion;

import java.nio.file.Path;
import java.util.Locale;
import org.jodconverter.core.DocumentConverter;
import org.jodconverter.core.document.DefaultDocumentFormatRegistry;
import org.jodconverter.core.office.OfficeException;
import org.jodconverter.core.office.OfficeManager;
import org.springframework.stereotype.Component;

@Component
public class JodDocumentEngine implements DocumentEngine {

    private static final String QUEUE_TIMEOUT_PREFIX = "No office manager available after";

    private final DocumentConverter converter;
    private final OfficeManager officeManager;

    public JodDocumentEngine(DocumentConverter converter, OfficeManager officeManager) {
        this.converter = converter;
        this.officeManager = officeManager;
    }

    @Override
    public void convert(Path input, Path output, SourceFormat source) {
        if (!officeManager.isRunning()) {
            throw new ConversionException(ConversionException.Reason.RENDERER_UNAVAILABLE);
        }
        try {
            converter.convert(input.toFile())
                    .as(source.documentFormat())
                    .to(output.toFile())
                    .as(DefaultDocumentFormatRegistry.PDF)
                    .execute();
        } catch (OfficeException | IllegalStateException exception) {
            if (causedByInterruption(exception)) {
                Thread.currentThread().interrupt();
            }
            throw new ConversionException(reasonFor(exception), exception);
        }
    }

    static ConversionException.Reason reasonFor(Throwable error) {
        return isTimeout(error)
                ? ConversionException.Reason.TIMEOUT
                : ConversionException.Reason.RENDERER_UNAVAILABLE;
    }

    private static boolean isTimeout(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (current.getClass().getSimpleName().contains("Timeout")
                    || (message != null && message.startsWith(QUEUE_TIMEOUT_PREFIX))
                    || (message != null
                            && message.toLowerCase(Locale.ROOT).contains("timed out"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean causedByInterruption(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof InterruptedException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
