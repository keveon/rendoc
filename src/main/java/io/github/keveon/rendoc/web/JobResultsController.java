package io.github.keveon.rendoc.web;

import io.github.keveon.rendoc.jobs.Job;
import io.github.keveon.rendoc.jobs.JobStatus;
import io.github.keveon.rendoc.jobs.JobRepository;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 批量任务结果下载端点。
 *
 * <p>{@code GET /api/v1/jobs/{jobId}/results} — 以 ZIP 流式返回所有成功转换的 PDF。
 */
@RestController
@RequestMapping("/api/v1/jobs")
public class JobResultsController {

    private static final Logger LOGGER = LoggerFactory.getLogger(JobResultsController.class);

    private final JobRepository repository;

    public JobResultsController(JobRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/{jobId}/results")
    public ResponseEntity<?> downloadResults(@PathVariable String jobId, HttpServletResponse response)
            throws IOException {
        Job job = repository.findById(jobId);
        if (job == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    java.util.Map.of("error", "Job not found", "jobId", jobId));
        }

        if (job.status() != JobStatus.COMPLETED && job.status() != JobStatus.FAILED) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                    java.util.Map.of(
                            "error", "Job is not yet complete",
                            "status", job.status().name().toLowerCase(),
                            "succeeded", job.succeeded(),
                            "failed", job.failed(),
                            "total", job.total()));
        }

        List<Path> pdfs = job.items().stream()
                .filter(item -> item.outputPath() != null)
                .map(item -> Path.of(item.outputPath()))
                .filter(Files::isRegularFile)
                .toList();

        if (pdfs.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/zip");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"job-" + jobId + "-results.zip\"");

        try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream())) {
            for (int i = 0; i < pdfs.size(); i++) {
                Path pdf = pdfs.get(i);
                String entryName = String.format("output-%03d.pdf", i);
                zos.putNextEntry(new ZipEntry(entryName));
                Files.copy(pdf, zos);
                zos.closeEntry();
            }
        }

        LOGGER.info("Downloaded {} PDFs for job {}", pdfs.size(), jobId);
        return null;
    }
}
