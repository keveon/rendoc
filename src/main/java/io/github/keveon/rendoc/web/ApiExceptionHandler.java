package io.github.keveon.rendoc.web;

import io.github.keveon.rendoc.conversion.ConversionException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ConversionException.class)
    public ResponseEntity<ProblemDetail> handleConversionException(ConversionException exception) {
        ProblemSpec spec = specification(exception.reason());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(spec.status(), spec.detail());
        problem.setTitle(spec.title());
        problem.setProperty("code", spec.code());

        var response = ResponseEntity.status(spec.status())
                .header(HttpHeaders.CACHE_CONTROL, "no-store");
        if (exception.reason() == ConversionException.Reason.CAPACITY_EXCEEDED
                || exception.reason() == ConversionException.Reason.RENDERER_UNAVAILABLE) {
            response.header(HttpHeaders.RETRY_AFTER, "5");
        }
        return response.body(problem);
    }

    private static ProblemSpec specification(ConversionException.Reason reason) {
        return switch (reason) {
            case UNSUPPORTED_FORMAT -> new ProblemSpec(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "unsupported_format",
                    "Unsupported document format",
                    "Only DOCX or XLSX input with PDF output is supported.");
            case UNSUPPORTED_MEDIA_TYPE -> new ProblemSpec(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "unsupported_media_type",
                    "Unsupported media type",
                    "Content-Type must match the declared source format or be application/octet-stream.");
            case PAYLOAD_TOO_LARGE -> new ProblemSpec(
                    HttpStatus.CONTENT_TOO_LARGE,
                    "payload_too_large",
                    "Payload too large",
                    "The uploaded document exceeds the configured size limit.");
            case CAPACITY_EXCEEDED -> new ProblemSpec(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "capacity_exceeded",
                    "Conversion capacity exhausted",
                    "The converter is busy. Retry after a short delay.");
            case RENDERER_UNAVAILABLE -> new ProblemSpec(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "renderer_unavailable",
                    "Renderer unavailable",
                    "LibreOffice is not ready to accept conversion work.");
            case TIMEOUT -> new ProblemSpec(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "conversion_timeout",
                    "Conversion timed out",
                    "The conversion did not finish within the configured time limit.");
            case INVALID_DOCUMENT -> new ProblemSpec(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "invalid_document",
                    "Document cannot be converted",
                    "The document is corrupt, invalid, or cannot be rendered as PDF.");
            case INTERNAL_ERROR -> new ProblemSpec(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "internal_error",
                    "Conversion failed",
                    "The conversion could not be completed.");
        };
    }

    private record ProblemSpec(HttpStatus status, String code, String title, String detail) {
    }
}
