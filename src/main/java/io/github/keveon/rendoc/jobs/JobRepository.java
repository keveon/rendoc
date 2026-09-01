package io.github.keveon.rendoc.jobs;

import java.time.Instant;
import java.util.List;

/**
 * 批量任务持久化仓库。
 *
 * <p>使用 Spring JDBC 操作 SQLite，提供任务和 item 的 CRUD 与原子状态流转。
 */
public interface JobRepository {

    /**
     * 创建任务及其所有 item。
     *
     * @param jobId        任务 ID
     * @param templatePath 模板文件路径
     * @param sourceFormat 源格式
     * @param itemsData    每项的 JSON 数据列表
     * @return 创建的任务实体（不含 item 列表）
     */
    Job createJob(String jobId, String templatePath, String sourceFormat, List<String> itemsData);

    /**
     * 按任务 ID 查询，包含 item 列表。
     *
     * @return 任务实体，或 null
     */
    Job findById(String jobId);

    /**
     * 领取待处理的 item（原子操作：将 pending → processing），同时将 job 标记为 processing。
     *
     * @param limit 最大领取数量
     * @return 被领取的 item 列表（可能为空）
     */
    List<JobItem> claimNextPendingItems(int limit);

    /**
     * 标记 item 成功并更新 job 进度。
     */
    void markItemSucceeded(String itemId, String outputPath);

    /**
     * 标记 item 失败并更新 job 进度。
     */
    void markItemFailed(String itemId, String errorMessage);

    /**
     * 更新 job 进度计数；当所有 item 处理完毕时标记 job 为 completed。
     */
    void updateJobProgress(String jobId);

    /**
     * 删除任务及其所有 item 和输出文件。
     */
    void deleteJob(String jobId);

    /**
     * 查询已完成的、超过保留期的任务 ID 列表。
     */
    List<String> findExpiredCompletedJobs(Instant cutoff);
}
