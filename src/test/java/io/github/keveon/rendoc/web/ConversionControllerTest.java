package io.github.keveon.rendoc.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.keveon.rendoc.conversion.ConversionException;
import io.github.keveon.rendoc.conversion.ConversionResult;
import io.github.keveon.rendoc.conversion.ConversionService;
import io.github.keveon.rendoc.conversion.SourceFormat;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.DelegatingServletOutputStream;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ConversionControllerTest {

    @TempDir
    Path tempDirectory;

    @Test
    void returnsAStreamedPdfForAValidRequest() throws Exception {
        var service = mock(ConversionService.class);
        var workDirectory = Files.createDirectory(tempDirectory.resolve("work"));
        var pdf = workDirectory.resolve("output.pdf");
        Files.writeString(pdf, "%PDF-1.7\nconverted", StandardCharsets.US_ASCII);
        byte[] expectedPdf = Files.readAllBytes(pdf);
        when(service.convert(any(InputStream.class), eq(8L), eq(SourceFormat.DOCX)))
                .thenReturn(ConversionResult.owned(pdf, workDirectory));
        var mockMvc = mockMvc(service);

        mockMvc.perform(post("/api/v1/convert")
                        .queryParam("source", "docx")
                        .queryParam("target", "pdf")
                        .contentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                        .accept(MediaType.APPLICATION_PDF)
                        .content("document"))
                .andExpect(request().asyncNotStarted())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=converted.pdf"))
                .andExpect(content().bytes(expectedPdf));

        assertThat(workDirectory).doesNotExist();
    }

    @Test
    void cleansTheWorkspaceWhenResponseStreamingFails() throws Exception {
        var service = mock(ConversionService.class);
        var workDirectory = Files.createDirectory(tempDirectory.resolve("failed-stream"));
        var pdf = workDirectory.resolve("output.pdf");
        Files.writeString(pdf, "%PDF-1.7\nconverted", StandardCharsets.US_ASCII);
        when(service.convert(any(InputStream.class), eq(8L), eq(SourceFormat.DOCX)))
                .thenReturn(ConversionResult.owned(pdf, workDirectory));
        var request = new MockHttpServletRequest("POST", "/api/v1/convert");
        request.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        request.setContent("document".getBytes(StandardCharsets.UTF_8));
        var response = new MockHttpServletResponse() {
            @Override
            public DelegatingServletOutputStream getOutputStream() {
                return new DelegatingServletOutputStream(new OutputStream() {
                    @Override
                    public void write(int value) throws IOException {
                        throw new IOException("simulated client disconnect");
                    }
                });
            }
        };

        var controller = new ConversionController(service);
        assertThatThrownBy(() -> controller.convert("docx", "pdf", request, response))
                .isInstanceOf(IOException.class);
        assertThat(workDirectory).doesNotExist();
    }

    @Test
    void rejectsAContentTypeThatDoesNotMatchTheDeclaredSource() throws Exception {
        var service = mock(ConversionService.class);
        var mockMvc = mockMvc(service);

        mockMvc.perform(post("/api/v1/convert")
                        .queryParam("source", "xlsx")
                        .queryParam("target", "pdf")
                        .contentType(MediaType.APPLICATION_PDF)
                        .accept(MediaType.APPLICATION_PDF)
                        .content("document"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("unsupported_media_type"));

        verifyNoInteractions(service);
    }

    @Test
    void mapsMalformedContentTypesToAControlledProblemResponse() throws Exception {
        var service = mock(ConversionService.class);
        var mockMvc = mockMvc(service);

        mockMvc.perform(post("/api/v1/convert")
                        .queryParam("source", "docx")
                        .queryParam("target", "pdf")
                        .header(HttpHeaders.CONTENT_TYPE, "not a media type")
                        .content("document"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.code").value("unsupported_media_type"));

        verifyNoInteractions(service);
    }

    @Test
    void mapsCapacityExhaustionToTooManyRequests() throws Exception {
        var service = mock(ConversionService.class);
        when(service.convert(any(InputStream.class), eq(8L), eq(SourceFormat.DOCX)))
                .thenThrow(new ConversionException(ConversionException.Reason.CAPACITY_EXCEEDED));
        var mockMvc = mockMvc(service);

        mockMvc.perform(post("/api/v1/convert")
                        .queryParam("source", "docx")
                        .queryParam("target", "pdf")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .accept(MediaType.APPLICATION_PDF)
                        .content("document"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "5"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.code").value("capacity_exceeded"));
    }

    @Test
    void mapsRendererOutagesToRetryableServiceUnavailable() throws Exception {
        var service = mock(ConversionService.class);
        when(service.convert(any(InputStream.class), eq(8L), eq(SourceFormat.DOCX)))
                .thenThrow(new ConversionException(ConversionException.Reason.RENDERER_UNAVAILABLE));
        var mockMvc = mockMvc(service);

        mockMvc.perform(post("/api/v1/convert")
                        .queryParam("source", "docx")
                        .queryParam("target", "pdf")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .accept(MediaType.APPLICATION_PDF)
                        .content("document"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "5"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.code").value("renderer_unavailable"));
    }

    @Test
    void rejectsUnsupportedTargets() throws Exception {
        var service = mock(ConversionService.class);
        var mockMvc = mockMvc(service);

        mockMvc.perform(post("/api/v1/convert")
                        .queryParam("source", "docx")
                        .queryParam("target", "html")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content("document"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("unsupported_format"));

        verifyNoInteractions(service);
    }

    private MockMvc mockMvc(ConversionService service) {
        return MockMvcBuilders.standaloneSetup(new ConversionController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }
}
