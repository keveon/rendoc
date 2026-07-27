package io.github.keveon.rendoc.conversion;

public class ConversionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public enum Reason {
        UNSUPPORTED_FORMAT,
        UNSUPPORTED_MEDIA_TYPE,
        PAYLOAD_TOO_LARGE,
        CAPACITY_EXCEEDED,
        RENDERER_UNAVAILABLE,
        TIMEOUT,
        INVALID_DOCUMENT,
        INTERNAL_ERROR
    }

    private final Reason reason;

    public ConversionException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public ConversionException(Reason reason, Throwable cause) {
        super(reason.name(), cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
