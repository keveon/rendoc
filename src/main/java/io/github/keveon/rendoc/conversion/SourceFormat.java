package io.github.keveon.rendoc.conversion;

import java.util.Locale;
import org.jodconverter.core.document.DefaultDocumentFormatRegistry;
import org.jodconverter.core.document.DocumentFormat;
import org.springframework.http.MediaType;

public enum SourceFormat {
    DOCX(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            DefaultDocumentFormatRegistry.DOCX),
    XLSX(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            DefaultDocumentFormatRegistry.XLSX);

    private final MediaType mediaType;
    private final DocumentFormat documentFormat;

    SourceFormat(String mediaType, DocumentFormat documentFormat) {
        this.mediaType = MediaType.parseMediaType(mediaType);
        this.documentFormat = documentFormat;
    }

    public static SourceFormat parse(String value) {
        if (value == null) {
            throw new ConversionException(ConversionException.Reason.UNSUPPORTED_FORMAT);
        }
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ConversionException(
                    ConversionException.Reason.UNSUPPORTED_FORMAT, exception);
        }
    }

    public boolean accepts(MediaType contentType) {
        return MediaType.APPLICATION_OCTET_STREAM.equals(contentType)
                || mediaType.isCompatibleWith(contentType);
    }

    public DocumentFormat documentFormat() {
        return documentFormat;
    }

    public String extension() {
        return name().toLowerCase(Locale.ROOT);
    }
}
