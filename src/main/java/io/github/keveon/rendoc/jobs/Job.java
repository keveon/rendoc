package io.github.keveon.rendoc.jobs;

import java.time.Instant;
import java.util.List;

/**
 * 批量任务实体。
 *
 * @param id            任务 ID（UUID）
 * @param status        任务状态
 * @param total         总 item 数
 * @param succeeded     成功 item 数
 * @param failed        失败 item 数
 * @param templatePath  模板文件持久化路径
 * @param sourceFormat  模板源格式（docx）
 * @param createdAt     创建时间
 * @param completedAt   完成时间（可能为 null）
 * @param items         item 列表（可能为 null，取决于查询方式）
 */
public record Job(
        String id,
        JobStatus status,
        int total,
        int succeeded,
        int failed,
        String templatePath,
        String sourceFormat,
        Instant createdAt,
        Instant completedAt,
        List<JobItem> items) {
}
