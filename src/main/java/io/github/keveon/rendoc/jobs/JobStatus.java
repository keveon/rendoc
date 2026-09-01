package io.github.keveon.rendoc.jobs;

/**
 * 批量任务状态机。
 *
 * <pre>
 * pending → processing → completed
 *                    ↘ failed (全部 item 失败时)
 * </pre>
 */
public enum JobStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
}
