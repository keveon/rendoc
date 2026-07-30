package io.github.keveon.rendoc.jobs;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.github.keveon.rendoc.config.JobProperties;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 基于 Spring JDBC + SQLite 的 JobRepository 实现。
 *
 * <p>所有状态流转使用事务保证原子性。claimNextPendingItems 使用
 * {@code UPDATE ... SET status='processing' WHERE id IN (SELECT ... pending LIMIT N)}
 * 模式实现乐观锁领取。
 */
@Repository
public class JdbcJobRepository implements JobRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcJobRepository.class);
    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<java.util.Map<String, Object>>() {}.getType();

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public JdbcJobRepository(JdbcTemplate jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    @Override
    public Job createJob(String jobId, String templatePath, String sourceFormat, List<String> itemsData) {
        Instant now = Instant.now();
        String nowStr = now.toString();

        tx.executeWithoutResult(status -> {
            jdbc.update(
                    "INSERT INTO jobs (id, status, total, succeeded, failed, template_path, source_format, created_at) "
                            + "VALUES (?, 'pending', ?, 0, 0, ?, ?, ?)",
                    jobId, itemsData.size(), templatePath, sourceFormat, nowStr);

            for (int i = 0; i < itemsData.size(); i++) {
                String itemId = UUID.randomUUID().toString();
                jdbc.update(
                        "INSERT INTO job_items (id, job_id, seq, status, data_json, created_at) "
                                + "VALUES (?, ?, ?, 'pending', ?, ?)",
                        itemId, jobId, i, itemsData.get(i), nowStr);
            }
        });

        LOGGER.info("Created job {} with {} items", jobId, itemsData.size());
        return new Job(jobId, JobStatus.PENDING, itemsData.size(), 0, 0,
                templatePath, sourceFormat, now, null, null);
    }

    @Override
    public Job findById(String jobId) {
        List<JobItem> items = jdbc.query(
                "SELECT * FROM job_items WHERE job_id = ? ORDER BY seq",
                (rs, n) -> mapItem(rs),
                jobId);

        List<Job> jobs = jdbc.query(
                "SELECT * FROM jobs WHERE id = ?",
                (rs, n) -> mapJob(rs, items),
                jobId);

        return jobs.isEmpty() ? null : jobs.get(0);
    }

    @Override
    public List<JobItem> claimNextPendingItems(int limit) {
        return tx.execute(status -> {
            // 原子领取：先查 pending item IDs，再批量更新为 processing
            List<String> ids = jdbc.queryForList(
                    "SELECT id FROM job_items WHERE status = 'pending' ORDER BY created_at LIMIT ?",
                    String.class, limit);

            if (ids.isEmpty()) {
                return List.of();
            }

            String placeholders = String.join(",", ids.stream().map(x -> "?").toList());
            String now = Instant.now().toString();

            // 批量标记为 processing
            jdbc.update(
                    "UPDATE job_items SET status = 'processing', started_at = ? WHERE id IN (" + placeholders + ")",
                    appendArg(now, ids));

            // 标记对应 job 为 processing（仅 pending 状态的）
            List<String> jobIds = jdbc.queryForList(
                    "SELECT DISTINCT job_id FROM job_items WHERE id IN (" + placeholders + ")",
                    String.class, ids.toArray());

            for (String jobId : jobIds) {
                jdbc.update("UPDATE jobs SET status = 'processing' WHERE id = ? AND status = 'pending'", jobId);
            }

            // 返回被领取的 items
            return jdbc.query(
                    "SELECT * FROM job_items WHERE id IN (" + placeholders + ") ORDER BY seq",
                    (rs, n) -> mapItem(rs),
                    ids.toArray());
        });
    }

    @Override
    public void markItemSucceeded(String itemId, String outputPath) {
        String now = Instant.now().toString();
        String jobId = jdbc.queryForObject(
                "SELECT job_id FROM job_items WHERE id = ?", String.class, itemId);
        if (jobId == null) return;

        tx.executeWithoutResult(status -> {
            jdbc.update(
                    "UPDATE job_items SET status = 'done', output_path = ?, completed_at = ? WHERE id = ?",
                    outputPath, now, itemId);
        });
        updateJobProgress(jobId);
    }

    @Override
    public void markItemFailed(String itemId, String errorMessage) {
        String now = Instant.now().toString();
        String jobId = jdbc.queryForObject(
                "SELECT job_id FROM job_items WHERE id = ?", String.class, itemId);
        if (jobId == null) return;

        tx.executeWithoutResult(status -> {
            jdbc.update(
                    "UPDATE job_items SET status = 'failed', error_message = ?, completed_at = ? WHERE id = ?",
                    errorMessage, now, itemId);
        });
        updateJobProgress(jobId);
    }

    @Override
    public void updateJobProgress(String jobId) {
        tx.executeWithoutResult(status -> {
            Integer succeeded = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM job_items WHERE job_id = ? AND status = 'done'",
                    Integer.class, jobId);
            Integer failed = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM job_items WHERE job_id = ? AND status = 'failed'",
                    Integer.class, jobId);
            Integer pendingOrProcessing = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM job_items WHERE job_id = ? AND status IN ('pending', 'processing')",
                    Integer.class, jobId);

            succeeded = succeeded == null ? 0 : succeeded;
            failed = failed == null ? 0 : failed;

            jdbc.update(
                    "UPDATE jobs SET succeeded = ?, failed = ? WHERE id = ?",
                    succeeded, failed, jobId);

            // 当没有 pending/processing 的 item 时，标记 job 为 completed
            if (pendingOrProcessing != null && pendingOrProcessing == 0) {
                JobStatus finalStatus = (failed > 0 && succeeded == 0) ? JobStatus.FAILED : JobStatus.COMPLETED;
                jdbc.update(
                        "UPDATE jobs SET status = ?, completed_at = ? WHERE id = ?",
                        finalStatus.name().toLowerCase(), Instant.now().toString(), jobId);
                LOGGER.info("Job {} completed: succeeded={}, failed={}", jobId, succeeded, failed);
            }
        });
    }

    @Override
    public void deleteJob(String jobId) {
        tx.executeWithoutResult(status -> {
            // 删除输出文件
            List<String> outputPaths = jdbc.queryForList(
                    "SELECT output_path FROM job_items WHERE job_id = ? AND output_path IS NOT NULL",
                    String.class, jobId);
            for (String path : outputPaths) {
                try {
                    Files.deleteIfExists(Path.of(path));
                } catch (Exception e) {
                    LOGGER.warn("Failed to delete output file: {}", path, e);
                }
            }
            // 删除模板文件
            String templatePath = jdbc.queryForObject(
                    "SELECT template_path FROM jobs WHERE id = ?", String.class, jobId);
            if (templatePath != null) {
                try {
                    Files.deleteIfExists(Path.of(templatePath));
                } catch (Exception e) {
                    LOGGER.warn("Failed to delete template file: {}", templatePath, e);
                }
            }
            // 删除 job 目录
            try {
                Path jobDir = Path.of(templatePath != null ? templatePath : "").getParent();
                if (jobDir != null && Files.isDirectory(jobDir)) {
                    Files.deleteIfExists(jobDir);
                }
            } catch (Exception e) {
                LOGGER.debug("Job dir cleanup skipped", e);
            }
            // 删除 DB 记录（级联删除 items）
            jdbc.update("DELETE FROM job_items WHERE job_id = ?", jobId);
            jdbc.update("DELETE FROM jobs WHERE id = ?", jobId);
        });
    }

    @Override
    public List<String> findExpiredCompletedJobs(Instant cutoff) {
        return jdbc.queryForList(
                "SELECT id FROM jobs WHERE completed_at IS NOT NULL AND completed_at < ? "
                        + "AND status IN ('completed', 'failed')",
                String.class, cutoff.toString());
    }

    // --- row mappers ---

    private static Job mapJob(java.sql.ResultSet rs, List<JobItem> items) throws java.sql.SQLException {
        return new Job(
                rs.getString("id"),
                JobStatus.valueOf(rs.getString("status").toUpperCase()),
                rs.getInt("total"),
                rs.getInt("succeeded"),
                rs.getInt("failed"),
                rs.getString("template_path"),
                rs.getString("source_format"),
                parseInstant(rs.getString("created_at")),
                parseInstant(rs.getString("completed_at")),
                items);
    }

    private static JobItem mapItem(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new JobItem(
                rs.getString("id"),
                rs.getString("job_id"),
                rs.getInt("seq"),
                ItemStatus.valueOf(rs.getString("status").toUpperCase()),
                rs.getString("data_json"),
                rs.getString("output_path"),
                rs.getString("error_message"),
                parseInstant(rs.getString("created_at")),
                parseInstant(rs.getString("started_at")),
                parseInstant(rs.getString("completed_at")));
    }

    private static Instant parseInstant(String value) {
        return value == null ? null : Instant.parse(value);
    }

    private static Object[] appendArg(Object first, List<String> rest) {
        Object[] args = new Object[rest.size() + 1];
        args[0] = first;
        for (int i = 0; i < rest.size(); i++) {
            args[i + 1] = rest.get(i);
        }
        return args;
    }
}
