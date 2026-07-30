package io.github.keveon.rendoc.conversion;

import io.github.keveon.rendoc.config.ConversionProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 编排模板渲染与格式转换的顶层服务。
 *
 * <p>接受模板二进制和变量数据，先渲染为 DOCX，再转换为 PDF。
 * 内部使用 {@link TemplateRenderer} 渲染模板，再交给 {@link ConversionService} 完成格式转换。
 */
@Service
public class RenderService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RenderService.class);

    private final TemplateRenderer renderer;
    private final ConversionService conversionService;
    private final ConversionProperties properties;

    public RenderService(
            TemplateRenderer renderer,
            ConversionService conversionService,
            ConversionProperties properties) {
        this.renderer = renderer;
        this.conversionService = conversionService;
        this.properties = properties;
    }

    /**
     * 渲染模板并转换为 PDF。
     *
     * @param templateBytes 模板文件输入流
     * @param templateSize  模板大小（字节）
     * @param data          变量键值对
     * @return 转换结果（PDF 文件路径和临时目录所有权）
     */
    public ConversionResult renderToPdf(
            InputStream templateBytes, long templateSize, Map<String, Object> data) {
        if (templateSize > properties.maxInputBytes()) {
            throw new ConversionException(ConversionException.Reason.PAYLOAD_TOO_LARGE);
        }

        Path requestDirectory = null;
        try {
            Files.createDirectories(properties.workingDirectory());
            requestDirectory = Files.createTempDirectory(properties.workingDirectory(), "render-");
            Path templateFile = requestDirectory.resolve("template.docx");
            Path renderedDocx = requestDirectory.resolve("rendered.docx");

            long inputBytes = BoundedInput.copy(templateBytes, templateFile, properties.maxInputBytes());
            if (inputBytes == 0) {
                throw new ConversionException(ConversionException.Reason.INVALID_DOCUMENT);
            }

            renderer.render(templateFile, renderedDocx, data);
            ConversionResult result = conversionService.convert(
                    Files.newInputStream(renderedDocx), Files.size(renderedDocx), SourceFormat.DOCX);

            // 转换成功后删除渲染中间产物，只保留 PDF（ConversionResult 拥有 requestDirectory 的清理权）
            Files.deleteIfExists(renderedDocx);
            Files.deleteIfExists(templateFile);

            return result;
        } catch (ConversionException exception) {
            cleanUp(requestDirectory);
            throw exception;
        } catch (Exception exception) {
            cleanUp(requestDirectory);
            throw new ConversionException(ConversionException.Reason.INTERNAL_ERROR, exception);
        }
    }

    private static void cleanUp(Path directory) {
        if (directory == null) {
            return;
        }
        try {
            TemporaryFiles.deleteRecursively(directory);
        } catch (IOException e) {
            TemporaryFiles.scheduleDeletionOnExit(directory);
            LOGGER.error("Render workspace cleanup failed; deletion scheduled for process exit", e);
        }
    }
}
