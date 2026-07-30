package io.github.keveon.rendoc.conversion;

import java.nio.file.Path;
import java.util.Map;

/**
 * 模板渲染引擎抽象，将带占位符的 DOCX 模板与数据合并为渲染后的文档。
 *
 * <p>实现必须保证：模板来源完全不可信，渲染过程中不得执行任意代码或加载外部实体。
 */
@FunctionalInterface
public interface TemplateRenderer {

    /**
     * 将数据填充到模板并输出渲染后的文档。
     *
     * @param template 模板文件路径
     * @param output   输出文件路径
     * @param data     变量键值对；空 map 表示无变量替换
     * @throws Exception 渲染失败时抛出
     */
    void render(Path template, Path output, Map<String, Object> data) throws Exception;
}
