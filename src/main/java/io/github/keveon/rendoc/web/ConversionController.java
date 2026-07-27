package io.github.keveon.rendoc.web;

import io.github.keveon.rendoc.conversion.ConversionException;
import io.github.keveon.rendoc.conversion.ConversionResult;
import io.github.keveon.rendoc.conversion.ConversionService;
import io.github.keveon.rendoc.conversion.SourceFormat;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ConversionController {

    private final ConversionService conversionService;

    public ConversionController(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @PostMapping(value = "/convert", produces = MediaType.APPLICATION_PDF_VALUE)
    public void convert(
            @RequestParam String source,
            @RequestParam(defaultValue = "pdf") String target,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        requirePdfTarget(target);
        SourceFormat sourceFormat = SourceFormat.parse(source);
        MediaType contentType = parseContentType(request.getContentType());
        if (!sourceFormat.accepts(contentType)) {
            throw new ConversionException(ConversionException.Reason.UNSUPPORTED_MEDIA_TYPE);
        }

        ConversionResult result = conversionService.convert(
                request.getInputStream(), request.getContentLengthLong(), sourceFormat);
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_PDF_VALUE);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=converted.pdf");
        try (result) {
            result.transferTo(response.getOutputStream());
        }
    }

    private static void requirePdfTarget(String target) {
        if (!"pdf".equals(target.toLowerCase(Locale.ROOT))) {
            throw new ConversionException(ConversionException.Reason.UNSUPPORTED_FORMAT);
        }
    }

    private static MediaType parseContentType(String contentType) {
        if (contentType == null) {
            throw new ConversionException(ConversionException.Reason.UNSUPPORTED_MEDIA_TYPE);
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (InvalidMediaTypeException exception) {
            throw new ConversionException(
                    ConversionException.Reason.UNSUPPORTED_MEDIA_TYPE, exception);
        }
    }
}
