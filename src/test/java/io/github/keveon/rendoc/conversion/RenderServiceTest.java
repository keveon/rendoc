package io.github.keveon.rendoc.conversion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.keveon.rendoc.config.ConversionProperties;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * RenderService 单元测试。
 *
 * <p>验证编排逻辑：先渲染模板，再转换 PDF。使用 mock TemplateRenderer 和 ConversionService
 * 来隔离测试编排行为，不依赖真实 LibreOffice。
 */
class RenderServiceTest {

    @TempDir
    Path tempDir;

    private TemplateRenderer renderer;
    private ConversionService conversionService;
    private ConversionProperties properties;
    private RenderService renderService;

    @BeforeEach
    void setUp() throws IOException {
        renderer = mock(TemplateRenderer.class);
        conversionService = mock(ConversionService.class);
        properties = new ConversionProperties(20L * 1024 * 1024, 2, tempDir);
        renderService = new RenderService(renderer, conversionService, properties);
    }

    @Test
    void renderToPdf_invokesRendererThenConverter() throws Exception {
        byte[] templateBytes = "fake-docx".getBytes();
        Map<String, Object> data = Map.of("name", "测试");

        // 模拟渲染：将模板复制到输出路径
        doAnswer(invocation -> {
            Path template = invocation.getArgument(0);
            Path output = invocation.getArgument(1);
            Files.copy(template, output);
            return null;
        }).when(renderer).render(any(Path.class), any(Path.class), any());

        Path expectedPdf = createFakePdf(tempDir.resolve("output.pdf"));
        when(conversionService.convert(any(), anyLong(), eq(SourceFormat.DOCX)))
                .thenReturn(ConversionResult.owned(expectedPdf, tempDir));

        ConversionResult result = renderService.renderToPdf(
                new ByteArrayInputStream(templateBytes), templateBytes.length, data);

        assertThat(result).isNotNull();
        verify(renderer).render(any(Path.class), any(Path.class), org.mockito.ArgumentMatchers.<Map<String, Object>>any());
        verify(conversionService).convert(any(), anyLong(), eq(SourceFormat.DOCX));
    }

    @Test
    void renderToPdf_rendererFails_wrapsAsRenderFailed() throws Exception {
        byte[] templateBytes = "fake-docx".getBytes();
        doThrow(new ConversionException(ConversionException.Reason.RENDER_FAILED))
                .when(renderer)
                .render(any(Path.class), any(Path.class), org.mockito.ArgumentMatchers.<Map<String, Object>>any());

        assertThatThrownBy(() -> renderService.renderToPdf(
                new ByteArrayInputStream(templateBytes), templateBytes.length, Map.of("name", "x")))
                .isInstanceOf(ConversionException.class)
                .satisfies(e -> assertThat(((ConversionException) e).reason())
                        .isEqualTo(ConversionException.Reason.RENDER_FAILED));

        // 转换器不应该被调用
        verifyNoInteractions(conversionService);
    }

    @Test
    void renderToPdf_conversionFails_propagatesException() throws Exception {
        byte[] templateBytes = "fake-docx".getBytes();

        // 模拟渲染创建文件
        doAnswer(invocation -> {
            Path output = invocation.getArgument(1);
            Files.writeString(output, "rendered-docx-content");
            return null;
        }).when(renderer).render(any(Path.class), any(Path.class), any());

        when(conversionService.convert(any(), anyLong(), eq(SourceFormat.DOCX)))
                .thenThrow(new ConversionException(ConversionException.Reason.RENDERER_UNAVAILABLE));

        assertThatThrownBy(() -> renderService.renderToPdf(
                new ByteArrayInputStream(templateBytes), templateBytes.length, Map.of()))
                .isInstanceOf(ConversionException.class)
                .satisfies(e -> assertThat(((ConversionException) e).reason())
                        .isEqualTo(ConversionException.Reason.RENDERER_UNAVAILABLE));
    }

    @Test
    void renderToPdf_emptyInput_throwsInvalidDocument() {
        byte[] empty = new byte[0];

        assertThatThrownBy(() -> renderService.renderToPdf(
                new ByteArrayInputStream(empty), 0L, Map.of()))
                .isInstanceOf(ConversionException.class)
                .satisfies(e -> assertThat(((ConversionException) e).reason())
                        .isEqualTo(ConversionException.Reason.INVALID_DOCUMENT));
    }

    @Test
    void renderToPdf_payloadTooLarge_throwsPayloadTooLarge() {
        byte[] big = new byte[1];

        assertThatThrownBy(() -> renderService.renderToPdf(
                new ByteArrayInputStream(big), 999_999_999L, Map.of()))
                .isInstanceOf(ConversionException.class)
                .satisfies(e -> assertThat(((ConversionException) e).reason())
                        .isEqualTo(ConversionException.Reason.PAYLOAD_TOO_LARGE));
    }

    // --- helpers ---

    private static Path createFakePdf(Path path) throws IOException {
        Files.writeString(path, "%PDF-1.4\nfake\n%%EOF");
        return path;
    }
}
