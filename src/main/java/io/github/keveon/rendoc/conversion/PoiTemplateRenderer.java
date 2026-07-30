package io.github.keveon.rendoc.conversion;

import com.deepoove.poi.XWPFTemplate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 基于 poi-tl 的模板渲染引擎实现。
 *
 * <p>将 DOCX 模板中的 {{placeholder}} 占位符替换为 data map 中的对应值，输出渲染后的 DOCX 文件。
 * 模板来源完全不可信，但 poi-tl 默认配置下仅做文本替换，不执行任意代码。
 */
@Component
public class PoiTemplateRenderer implements TemplateRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger(PoiTemplateRenderer.class);

    @Override
    public void render(Path template, Path output, Map<String, Object> data) {
        long startedAt = System.nanoTime();
        try (var is = Files.newInputStream(template);
         var os = Files.newOutputStream(output)) {
            XWPFTemplate document = XWPFTemplate.compile(is).render(data);
            document.write(os);
            document.close();
            LOGGER.info(
                    "Template rendering completed: variables={}, durationMs={}",
                    data.size(),
                    (System.nanoTime() - startedAt) / 1_000_000);
        } catch (Exception exception) {
            throw new ConversionException(ConversionException.Reason.RENDER_FAILED, exception);
        }
    }
}
