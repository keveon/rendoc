package io.github.keveon.rendoc.conversion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.deepoove.poi.XWPFTemplate;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * PoiTemplateRenderer 单元测试。
 *
 * <p>使用 fixture 模板 {@code template-simple.docx}（含 {{name}} {{amount}} 占位符）验证渲染行为。
 * 渲染后的 DOCX 通过 XWPFWordExtractor 提取纯文本，验证占位符已被替换。
 */
class PoiTemplateRendererTest {

    @TempDir
    Path tempDir;

    private PoiTemplateRenderer renderer;

    @BeforeEach
    void setUp() {
        renderer = new PoiTemplateRenderer();
    }

    @Test
    void render_simpleTemplate_fillsPlaceholders() throws Exception {
        Path template = copyFixture("template-simple.docx");
        Path output = tempDir.resolve("output.docx");

        renderer.render(template, output, Map.of("name", "张三", "amount", "1,000.00"));

        String text = extractDocxText(output);
        assertThat(text).contains("张三").contains("1,000.00");
        assertThat(text).doesNotContain("{{name}}").doesNotContain("{{amount}}");
    }

    @Test
    void render_emptyData_doesNotCrash() throws Exception {
        Path template = copyFixture("template-simple.docx");
        Path output = tempDir.resolve("output.docx");

        renderer.render(template, output, Map.of());

        assertThat(Files.isRegularFile(output)).isTrue();
        assertThat(Files.size(output)).isGreaterThan(0);
    }

    @Test
    void render_nonExistentTemplate_throwsRenderFailed() {
        Path nonExistent = tempDir.resolve("no-such-file.docx");
        Path output = tempDir.resolve("output.docx");

        assertThatThrownBy(() -> renderer.render(nonExistent, output, Map.of("name", "测试")))
                .isInstanceOf(ConversionException.class)
                .satisfies(e -> {
                    ConversionException ce = (ConversionException) e;
                    assertThat(ce.reason()).isEqualTo(ConversionException.Reason.RENDER_FAILED);
                });
    }

    @Test
    void render_outputIsWritableDocx() throws Exception {
        Path template = copyFixture("template-simple.docx");
        Path output = tempDir.resolve("rendered.docx");

        renderer.render(template, output, Map.of("name", "李四", "amount", "500"));

        try (XWPFDocument doc = new XWPFDocument(Files.newInputStream(output))) {
            assertThat(doc.getDocument()).isNotNull();
        }
    }

    // --- helpers ---

    private Path copyFixture(String name) throws Exception {
        Path target = tempDir.resolve(name);
        try (InputStream is = getClass().getResourceAsStream("/fixtures/" + name)) {
            assertThat(is).as("fixture %s must exist on classpath", name).isNotNull();
            Files.copy(is, target);
        }
        return target;
    }

    private static String extractDocxText(Path docx) throws Exception {
        try (XWPFDocument document = new XWPFDocument(Files.newInputStream(docx));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }
}
