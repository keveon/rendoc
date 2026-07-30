package io.github.keveon.rendoc.jobs;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.github.keveon.rendoc.config.JobProperties;
import io.github.keveon.rendoc.conversion.ConversionResult;
import io.github.keveon.rendoc.conversion.RenderService;
import io.github.keveon.rendoc.conversion.SourceFormat;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 异步批量任务处理器。
 *
 * <p>定时轮询 pending 的 job items，逐个渲染模板并转换为 PDF。
 * 成功后保存 PDF 到输出目录并更新 item 状态；失败时记录错误信息。
 */
@Component
public class JobWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(JobWorker.class);
    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();

    private final JobRepository repository;
    private final RenderService renderService;
    private final JobProperties properties;

    public JobWorker(JobRepository repository, RenderService renderService, JobProperties properties) {
        this.repository = repository;
        this.renderService = renderService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${rendoc.jobs.poll-interval:5s}")
    public void processPendingItems() {
        if (!properties.enabled()) {
            return;
        }

        List<JobItem> items = repository.claimNextPendingItems(properties.maxBatchSize());
        if (items.isEmpty()) {
            return;
        }

        LOGGER.debug("Processing {} pending items", items.size());

        for (JobItem item : items) {
            processItem(item);
        }
    }

    private void processItem(JobItem item) {
        try {
            // 加载模板
            Job job = repository.findById(item.jobId());
            if (job == null) {
                LOGGER.warn("Job {} not found for item {}", item.jobId(), item.id());
                repository.markItemFailed(item.id(), "Job not found");
                return;
            }

            Path templatePath = Path.of(job.templatePath());
            if (!Files.isRegularFile(templatePath)) {
                repository.markItemFailed(item.id(), "Template file not found: " + templatePath);
                return;
            }

            // 解析 data
            Map<String, Object> data = GSON.fromJson(item.dataJson(), MAP_TYPE);

            // 渲染 + 转换
            Path outputDir = properties.outputDir().resolve(job.id());
            Files.createDirectories(outputDir);
            Path outputPdf = outputDir.resolve(String.format("output-%03d.pdf", item.seq()));

            try (InputStream templateStream = Files.newInputStream(templatePath)) {
                ConversionResult result = renderService.renderToPdf(
                        templateStream, Files.size(templatePath), data);

                // 复制结果 PDF 到持久化路径
                try (result) {
                    Files.copy(result.path(), outputPdf);
                }
            }

            repository.markItemSucceeded(item.id(), outputPdf.toString());
            LOGGER.debug("Item {} succeeded: {}", item.id(), outputPdf);

        } catch (Exception e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            LOGGER.warn("Item {} failed: {}", item.id(), errorMsg, e);
            repository.markItemFailed(item.id(), errorMsg);
        }
    }
}
