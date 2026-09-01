package io.github.keveon.rendoc.jobs;

import io.github.keveon.rendoc.config.JobProperties;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时清理过期批量任务。
 *
 * <p>删除所有已完成且超过保留期的任务，同时清理关联的输出文件和模板文件。
 */
@Component
public class JobCleanupTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(JobCleanupTask.class);

    private final JobRepository repository;
    private final JobProperties properties;

    public JobCleanupTask(JobRepository repository, JobProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${rendoc.jobs.cleanup-interval:1h}")
    public void cleanupExpiredJobs() {
        if (!properties.enabled()) {
            return;
        }

        Instant cutoff = Instant.now().minus(properties.retention());
        List<String> expiredIds = repository.findExpiredCompletedJobs(cutoff);

        if (expiredIds.isEmpty()) {
            return;
        }

        LOGGER.info("Cleaning up {} expired jobs", expiredIds.size());
        for (String jobId : expiredIds) {
            try {
                repository.deleteJob(jobId);
            } catch (Exception e) {
                LOGGER.warn("Failed to clean up job {}", jobId, e);
            }
        }
    }
}
