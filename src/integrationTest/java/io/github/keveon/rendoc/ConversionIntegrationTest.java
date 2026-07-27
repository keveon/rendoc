package io.github.keveon.rendoc;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "rendoc.security.api-token=integration-secret-token",
            "rendoc.conversion.working-directory=${java.io.tmpdir}/rendoc-integration-${random.uuid}/work",
            "rendoc.libreoffice.working-directory=${java.io.tmpdir}/rendoc-integration-${random.uuid}/libreoffice",
            "rendoc.libreoffice.port=${random.int[20000,30000]}"
        })
class ConversionIntegrationTest {

    private static final String API_TOKEN = "integration-secret-token";

    @LocalServerPort private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @ParameterizedTest
    @CsvSource({
        "fixtures/sample.docx,docx,application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "fixtures/sample.xlsx,xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    })
    void convertsRealOfficeDocumentsToPdf(String fixture, String source, String contentType)
            throws Exception {
        byte[] document = requireFixture(fixture);
        var request = HttpRequest.newBuilder(conversionUri(source))
                .header("Authorization", "Bearer " + API_TOKEN)
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofByteArray(document))
                .build();

        HttpResponse<byte[]> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).hasValue("application/pdf");
        assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-store");
        assertThat(response.headers().firstValue("Content-Disposition"))
                .hasValue("attachment; filename=converted.pdf");
        assertThat(response.body().length).isGreaterThan(100);
        assertThat(response.body()).startsWith("%PDF-".getBytes(StandardCharsets.US_ASCII));
        assertThat(new String(response.body(), StandardCharsets.ISO_8859_1)).contains("%%EOF");
    }

    @Test
    void refusesConversionWithoutAuthentication() throws Exception {
        var request = HttpRequest.newBuilder(conversionUri("docx"))
                .header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofByteArray(requireFixture("fixtures/sample.docx")))
                .build();

        HttpResponse<byte[]> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-store");
        assertThat(new String(response.body(), StandardCharsets.UTF_8))
                .contains("authentication_required");
    }

    private URI conversionUri(String source) {
        return URI.create("http://127.0.0.1:" + port + "/api/v1/convert?source=" + source + "&target=pdf");
    }

    private static byte[] requireFixture(String path) throws Exception {
        try (var input = ConversionIntegrationTest.class.getClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("Missing integration fixture: " + path);
            }
            return input.readAllBytes();
        }
    }
}
