package io.github.keveon.rendoc;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * 批量任务端到端集成测试。
 *
 * <p>需要 LibreOffice 运行时。提交批量任务 → 轮询状态 → 下载结果 ZIP。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "rendoc.security.api-token=integration-secret-token",
            "rendoc.conversion.working-directory=${java.io.tmpdir}/rendoc-integration-${random.uuid}/work",
            "rendoc.libreoffice.working-directory=${java.io.tmpdir}/rendoc-integration-${random.uuid}/libreoffice",
            "rendoc.libreoffice.port=${random.int[20000,30000]}",
            "rendoc.jobs.output-dir=${java.io.tmpdir}/rendoc-integration-${random.uuid}/jobs",
            "rendoc.jobs.poll-interval=1s"
        })
class BatchJobIntegrationTest {

    private static final String API_TOKEN = "integration-secret-token";

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void batchJob_fullLifecycle() throws Exception {
        byte[] template = requireFixture("fixtures/template-simple.docx");
        String dataJson = "[{\"name\":\"张三\",\"amount\":\"100\"},{\"name\":\"李四\",\"amount\":\"200\"}]";
        String boundary = "rendoc-batch-" + System.nanoTime();

        String multipartBody = buildMultipartBody(boundary, template, dataJson);

        // 1. 提交批量任务
        var submitRequest = HttpRequest.newBuilder(jobsUri())
                .header("Authorization", "Bearer " + API_TOKEN)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofString(multipartBody))
                .build();

        HttpResponse<String> submitResponse =
                httpClient.send(submitRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertThat(submitResponse.statusCode()).isEqualTo(202);
        assertThat(submitResponse.body()).contains("jobId");

        // 提取 jobId
        String jobId = extractJobId(submitResponse.body());
        assertThat(jobId).isNotBlank();

        // 2. 轮询直到完成（最多 60 秒）
        String status = null;
        for (int i = 0; i < 60; i++) {
            Thread.sleep(1000);
            var statusRequest = HttpRequest.newBuilder(statusUri(jobId))
                    .header("Authorization", "Bearer " + API_TOKEN)
                    .GET()
                    .build();
            HttpResponse<String> statusResponse =
                    httpClient.send(statusRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            status = extractStatus(statusResponse.body());
            if ("completed".equals(status) || "failed".equals(status)) {
                break;
            }
        }

        assertThat(status).isEqualTo("completed");

        // 3. 下载结果 ZIP
        var downloadRequest = HttpRequest.newBuilder(resultsUri(jobId))
                .header("Authorization", "Bearer " + API_TOKEN)
                .GET()
                .build();
        HttpResponse<byte[]> downloadResponse =
                httpClient.send(downloadRequest, HttpResponse.BodyHandlers.ofByteArray());

        assertThat(downloadResponse.statusCode()).isEqualTo(200);
        assertThat(downloadResponse.headers().firstValue("Content-Type")).hasValue("application/zip");
        assertThat(downloadResponse.body().length).isGreaterThan(100);

        // 4. 验证返回的 404
        var notFoundRequest = HttpRequest.newBuilder(statusUri("nonexistent-job"))
                .header("Authorization", "Bearer " + API_TOKEN)
                .GET()
                .build();
        HttpResponse<String> notFoundResponse =
                httpClient.send(notFoundRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertThat(notFoundResponse.statusCode()).isEqualTo(404);
    }

    private URI jobsUri() {
        return URI.create("http://127.0.0.1:" + port + "/api/v1/jobs");
    }

    private URI statusUri(String jobId) {
        return URI.create("http://127.0.0.1:" + port + "/api/v1/jobs/" + jobId);
    }

    private URI resultsUri(String jobId) {
        return URI.create("http://127.0.0.1:" + port + "/api/v1/jobs/" + jobId + "/results");
    }

    private static String extractJobId(String json) {
        int idx = json.indexOf("\"jobId\":\"");
        if (idx < 0) return "";
        int start = idx + 9;
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }

    private static String extractStatus(String json) {
        int idx = json.indexOf("\"status\":\"");
        if (idx < 0) return "";
        int start = idx + 10;
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }

    private static String buildMultipartBody(String boundary, byte[] template, String dataJson) {
        var sb = new StringBuilder();
        sb.append("--").append(boundary).append("\r\n");
        sb.append("Content-Disposition: form-data; name=\"template\"; filename=\"template.docx\"\r\n");
        sb.append("Content-Type: application/octet-stream\r\n\r\n");
        sb.append(new String(template, StandardCharsets.ISO_8859_1));
        sb.append("\r\n");
        sb.append("--").append(boundary).append("\r\n");
        sb.append("Content-Disposition: form-data; name=\"data\"\r\n");
        sb.append("Content-Type: application/json\r\n\r\n");
        sb.append(dataJson);
        sb.append("\r\n");
        sb.append("--").append(boundary).append("--\r\n");
        return sb.toString();
    }

    private static byte[] requireFixture(String path) throws Exception {
        try (var input = BatchJobIntegrationTest.class.getClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("Missing integration fixture: " + path);
            }
            return input.readAllBytes();
        }
    }
}
