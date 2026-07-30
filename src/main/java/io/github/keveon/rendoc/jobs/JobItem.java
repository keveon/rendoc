package io.github.keveon.rendoc.jobs;

import java.time.Instant;

/**
 * 批量任务中的单个处理项。
 *
 * @param id            item ID（UUID）
 * @param jobId         所属任务 ID
 * @param seq           序号（0-based）
 * @param status        处理状态
 * @param dataJson      渲染数据（JSON 字符串）
 * @param outputPath    输出 PDF 路径（成功后设置）
 * @param errorMessage  失败原因（失败后设置）
 * @param createdAt     创建时间
 * @param startedAt     开始处理时间
 * @param completedAt   完成处理时间
 */
public record JobItem(
        String id,
        String jobId,
        int seq,
        ItemStatus status,
        String dataJson,
        String outputPath,
        String errorMessage,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt) {
}
