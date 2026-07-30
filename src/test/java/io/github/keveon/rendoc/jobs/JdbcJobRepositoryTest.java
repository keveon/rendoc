package io.github.keveon.rendoc.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * JdbcJobRepository 单元测试。
 *
 * <p>使用真实 SQLite 文件数据库（在 @TempDir 中）执行 schema 初始化和 CRUD 验证。
 */
class JdbcJobRepositoryTest {

    @TempDir
    Path tempDir;

    private JdbcJobRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        Path dbFile = tempDir.resolve("test.db");
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.sqlite.JDBC");
        ds.setUrl("jdbc:sqlite:" + dbFile);
        JdbcTemplate jdbc = new JdbcTemplate(ds);

        // 执行 schema
        String schema = Files.readString(Path.of("src/main/resources/schema.sql"));
        // SQLite 可以一次执行多条语句
        for (String stmt : schema.split(";")) {
            String trimmed = stmt.trim();
            if (!trimmed.isEmpty()) {
                jdbc.execute(trimmed);
            }
        }

        TransactionTemplate tx = new TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(ds));
        repository = new JdbcJobRepository(jdbc, tx);
    }

    @Test
    void createJob_insertsJobAndItems() {
        Job job = repository.createJob("job-1", "/tmp/template.docx", "docx",
                List.of("{\"name\":\"A\"}", "{\"name\":\"B\"}", "{\"name\":\"C\"}"));

        assertThat(job.status()).isEqualTo(JobStatus.PENDING);
        assertThat(job.total()).isEqualTo(3);

        Job found = repository.findById("job-1");
        assertThat(found).isNotNull();
        assertThat(found.items()).hasSize(3);
        assertThat(found.items().get(0).status()).isEqualTo(ItemStatus.PENDING);
        assertThat(found.items().get(0).seq()).isEqualTo(0);
    }

    @Test
    void claimNextPendingItems_marksAsProcessing() {
        repository.createJob("job-2", "/tmp/t.docx", "docx",
                List.of("{\"n\":1}", "{\"n\":2}", "{\"n\":3}"));

        List<JobItem> claimed = repository.claimNextPendingItems(2);

        assertThat(claimed).hasSize(2);
        assertThat(claimed).allMatch(item -> item.status() == ItemStatus.PROCESSING);

        Job job = repository.findById("job-2");
        assertThat(job.status()).isEqualTo(JobStatus.PROCESSING);
    }

    @Test
    void claimNextPendingItems_emptyWhenNothingPending() {
        List<JobItem> claimed = repository.claimNextPendingItems(5);
        assertThat(claimed).isEmpty();
    }

    @Test
    void markItemSucceeded_updatesJobProgress() {
        repository.createJob("job-3", "/tmp/t.docx", "docx",
                List.of("{\"n\":1}", "{\"n\":2}"));

        List<JobItem> items = repository.claimNextPendingItems(2);
        repository.markItemSucceeded(items.get(0).id(), "/tmp/output-0.pdf");

        Job job = repository.findById("job-3");
        assertThat(job.succeeded()).isEqualTo(1);
        // 还有 1 个 processing，不应该 completed
        assertThat(job.status()).isEqualTo(JobStatus.PROCESSING);
    }

    @Test
    void markItemSucceeded_allDone_completesJob() {
        repository.createJob("job-4", "/tmp/t.docx", "docx", List.of("{\"n\":1}"));

        List<JobItem> items = repository.claimNextPendingItems(1);
        repository.markItemSucceeded(items.get(0).id(), "/tmp/output.pdf");

        Job job = repository.findById("job-4");
        assertThat(job.status()).isEqualTo(JobStatus.COMPLETED);
        assertThat(job.succeeded()).isEqualTo(1);
        assertThat(job.completedAt()).isNotNull();
    }

    @Test
    void markItemFailed_updatesCounts() {
        repository.createJob("job-5", "/tmp/t.docx", "docx", List.of("{\"n\":1}", "{\"n\":2}"));

        List<JobItem> items = repository.claimNextPendingItems(2);
        repository.markItemFailed(items.get(0).id(), "Conversion timeout");

        Job job = repository.findById("job-5");
        assertThat(job.failed()).isEqualTo(1);
    }

    @Test
    void markItemFailed_allFailed_marksJobFailed() {
        repository.createJob("job-6", "/tmp/t.docx", "docx", List.of("{\"n\":1}"));

        List<JobItem> items = repository.claimNextPendingItems(1);
        repository.markItemFailed(items.get(0).id(), "Error");

        Job job = repository.findById("job-6");
        assertThat(job.status()).isEqualTo(JobStatus.FAILED);
        assertThat(job.failed()).isEqualTo(1);
        assertThat(job.succeeded()).isEqualTo(0);
    }

    @Test
    void mixedSuccessAndFailure_marksCompleted() {
        repository.createJob("job-7", "/tmp/t.docx", "docx",
                List.of("{\"n\":1}", "{\"n\":2}"));

        List<JobItem> items = repository.claimNextPendingItems(2);
        repository.markItemSucceeded(items.get(0).id(), "/tmp/ok.pdf");
        repository.markItemFailed(items.get(1).id(), "Bad template");

        Job job = repository.findById("job-7");
        assertThat(job.status()).isEqualTo(JobStatus.COMPLETED);
        assertThat(job.succeeded()).isEqualTo(1);
        assertThat(job.failed()).isEqualTo(1);
    }

    @Test
    void deleteJob_removesAllRecords() {
        repository.createJob("job-8", "/tmp/t.docx", "docx", List.of("{\"n\":1}"));

        repository.deleteJob("job-8");

        assertThat(repository.findById("job-8")).isNull();
    }

    @Test
    void findExpiredCompletedJobs_returnsOldCompletedOnly() {
        repository.createJob("job-old", "/tmp/t.docx", "docx", List.of("{\"n\":1}"));
        List<JobItem> items = repository.claimNextPendingItems(1);
        repository.markItemSucceeded(items.get(0).id(), "/tmp/ok.pdf");

        // future cutoff = everything is expired
        Instant cutoff = Instant.now().plus(1, ChronoUnit.SECONDS);
        List<String> expired = repository.findExpiredCompletedJobs(cutoff);
        assertThat(expired).contains("job-old");
    }

    @Test
    void findById_returnsNullForNonExistent() {
        assertThat(repository.findById("no-such-job")).isNull();
    }
}
