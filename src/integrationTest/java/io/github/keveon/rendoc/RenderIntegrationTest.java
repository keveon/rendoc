package io.github.keveon.rendoc;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * 模板渲染端到端集成测试。
 *
 * <p>需要 LibreOffice 运行时。使用 fixture 模板 template-simple.docx（含 {{name}} {{amount}} 占位符）。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "rendoc.security.api-token=integration-secret-token",
            "rendoc.conversion.working-directory=${java.io.tmpdir}/rendoc-integration-${random.uuid}/work",
            "rendoc.libreoffice.working-directory=${java.io.tmpdir}/rendoc-integration-${random.uuid}/libreoffice",
            "rendoc.libreoffice.port=${random.int[20000,30000]}"
        })
class RenderIntegrationTest {

    private static final String API_TOKEN = "integration-secret-token";

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void rendersTemplateToPdf_endToEnd() throws Exception {
        byte[] template = requireFixture("fixtures/template-simple.docx");
        String dataJson = "{\"name\":\"张三\",\"amount\":\"1,000.00\"}";
        String boundary = "rendoc-boundary-" + System.nanoTime();

        String multipartBody = buildMultipartBody(boundary, template, dataJson);

        var request = HttpRequest.newBuilder(renderUri())
                .header("Authorization", "Bearer " + API_TOKEN)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofString(multipartBody))
                .build();

        HttpResponse<byte[]> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).hasValue("application/pdf");
        assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-store");
        assertThat(response.headers().firstValue("Content-Disposition"))
                .hasValue("attachment; filename=rendered.pdf");
        assertThat(response.body()).startsWith("%PDF-".getBytes(StandardCharsets.US_ASCII));
        assertThat(new String(response.body(), StandardCharsets.ISO_8859_1)).contains("%%EOF");
    }

    private URI renderUri() {
        return URI.create("http://127.0.0.1:" + port + "/api/v1/render?target=pdf");
    }

    private static String buildMultipartBody(String boundary, byte[] template, String dataJson) {
        var sb = new StringBuilder();
        // template part
        sb.append("--").append(boundary).append("\r\n");
        sb.append("Content-Disposition: form-data; name=\"template\"; filename=\"template.docx\"\r\n");
        sb.append("Content-Type: application/octet-stream\r\n\r\n");
        // binary template encoded as ISO-8859-1 to preserve bytes
        sb.append(new String(template, StandardCharsets.ISO_8859_1));
        sb.append("\r\n");
        // data part
        sb.append("--").append(boundary).append("\r\n");
        sb.append("Content-Disposition: form-data; name=\"data\"\r\n");
        sb.append("Content-Type: application/json\r\n\r\n");
        sb.append(dataJson);
        sb.append("\r\n");
        sb.append("--").append(boundary).append("--\r\n");
        return sb.toString();
    }

    private static byte[] requireFixture(String path) throws Exception {
        try (var input = RenderIntegrationTest.class.getClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("Missing integration fixture: " + path);
            }
            return input.readAllBytes();
        }
    }
}
