package io.github.keveon.rendoc.web;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import io.github.keveon.rendoc.config.JobProperties;
import io.github.keveon.rendoc.jobs.Job;
import io.github.keveon.rendoc.jobs.JobRepository;
import io.github.keveon.rendoc.jobs.JobStatus;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 批量任务 API。
 *
 * <ul>
 *   <li>{@code POST /api/v1/jobs} — 提交批量任务（模板 + JSON 数据数组）</li>
 *   <li>{@code GET /api/v1/jobs/{jobId}} — 查询任务状态</li>
 *   <li>{@code DELETE /api/v1/jobs/{jobId}} — 删除任务及关联文件</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {

    private static final Logger LOGGER = LoggerFactory.getLogger(JobController.class);
    private static final Gson GSON = new Gson();
    private static final Type DATA_LIST_TYPE = new TypeToken<List<Map<String, Object>>>() {}.getType();

    private final JobRepository repository;
    private final JobProperties properties;

    public JobController(JobRepository repository, JobProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createJob(
            @RequestPart("template") MultipartFile template,
            @RequestPart("data") String dataJson,
            @RequestParam(defaultValue = "docx") String source,
            HttpServletResponse response) throws IOException {

        if (template.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Template part must not be empty"));
        }

        List<Map<String, Object>> dataList;
        try {
            dataList = GSON.fromJson(dataJson, DATA_LIST_TYPE);
        } catch (JsonSyntaxException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Data part must be a JSON array"));
        }

        if (dataList == null || dataList.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Data array must not be empty"));
        }

        if (dataList.size() > properties.maxBatchSize()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Batch size exceeds maximum",
                    "max", properties.maxBatchSize()));
        }

        // 持久化模板
        String jobId = UUID.randomUUID().toString();
        Path jobDir = properties.outputDir().resolve(jobId);
        Files.createDirectories(jobDir);
        Path templatePath = jobDir.resolve("template." + source);
        template.transferTo(templatePath);

        // 序列化每行数据
        List<String> itemsData = dataList.stream().map(GSON::toJson).toList();

        Job job = repository.createJob(jobId, templatePath.toString(), source, itemsData);

        LOGGER.info("Submitted batch job {} with {} items", jobId, itemsData.size());

        return ResponseEntity.accepted().body(Map.of(
                "jobId", jobId,
                "itemCount", job.total(),
                "status", job.status().name().toLowerCase()));
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<?> getJob(@PathVariable String jobId) {
        Job job = repository.findById(jobId);
        if (job == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", "Job not found",
                    "jobId", jobId));
        }

        return ResponseEntity.ok(Map.of(
                "jobId", job.id(),
                "status", job.status().name().toLowerCase(),
                "total", job.total(),
                "succeeded", job.succeeded(),
                "failed", job.failed(),
                "createdAt", job.createdAt() != null ? job.createdAt().toString() : null,
                "completedAt", job.completedAt() != null ? job.completedAt().toString() : null));
    }

    @DeleteMapping("/{jobId}")
    public ResponseEntity<Void> deleteJob(@PathVariable String jobId) {
        Job job = repository.findById(jobId);
        if (job == null) {
            return ResponseEntity.notFound().build();
        }
        repository.deleteJob(jobId);
        LOGGER.info("Deleted job {}", jobId);
        return ResponseEntity.noContent().build();
    }
}
