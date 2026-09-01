package io.github.keveon.rendoc.web;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import io.github.keveon.rendoc.conversion.ConversionResult;
import io.github.keveon.rendoc.conversion.RenderService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档模板渲染端点。
 *
 * <p>接受 multipart/form-data 请求：{@code template}（DOCX 模板二进制）和 {@code data}（JSON 变量键值对）。
 * 渲染模板并转换为 PDF 后流式返回。
 */
@RestController
@RequestMapping("/api/v1")
public class RenderController {

    private static final Gson GSON = new Gson();

    private final RenderService renderService;

    public RenderController(RenderService renderService) {
        this.renderService = renderService;
    }

    @PostMapping(value = "/render", produces = MediaType.APPLICATION_PDF_VALUE)
    public void render(
            @RequestPart("template") MultipartFile template,
            @RequestPart("data") String dataJson,
            @RequestParam(defaultValue = "pdf") String target,
            HttpServletResponse response) throws IOException {
        requirePdfTarget(target);

        if (template.isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Template part must not be empty");
            return;
        }

        Map<String, Object> data;
        try {
            data = parseData(dataJson);
        } catch (JsonSyntaxException exception) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Data part must be valid JSON");
            return;
        }

        ConversionResult result = renderService.renderToPdf(
                template.getInputStream(), template.getSize(), data);

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_PDF_VALUE);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=rendered.pdf");
        try (result) {
            result.transferTo(response.getOutputStream());
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseData(String json) {
        return GSON.fromJson(json, Map.class);
    }

    private static void requirePdfTarget(String target) {
        if (!"pdf".equals(target.toLowerCase(Locale.ROOT))) {
            throw new io.github.keveon.rendoc.conversion.ConversionException(
                    io.github.keveon.rendoc.conversion.ConversionException.Reason.UNSUPPORTED_FORMAT);
        }
    }
}
