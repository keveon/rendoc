package io.github.keveon.rendoc.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.keveon.rendoc.conversion.ConversionException;
import io.github.keveon.rendoc.conversion.ConversionResult;
import io.github.keveon.rendoc.conversion.RenderService;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RenderControllerTest {

    @TempDir
    Path tempDir;

    @Test
    void renderPdf_returnsPdfStream() throws Exception {
        var service = mock(RenderService.class);
        var pdf = tempDir.resolve("output.pdf");
        Files.writeString(pdf, "%PDF-1.7\nrendered");
        byte[] expected = Files.readAllBytes(pdf);
        when(service.renderToPdf(any(InputStream.class), anyLong(), any()))
                .thenReturn(ConversionResult.owned(pdf, tempDir));

        mockMvc(service)
                .perform(multipart("/api/v1/render")
                        .file(new MockMultipartFile("template", "template.docx",
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                "fake-docx".getBytes()))
                        .file(new MockMultipartFile("data", "",
                                MediaType.APPLICATION_JSON_VALUE,
                                "{\"name\":\"测试\"}".getBytes()))
                        .param("target", "pdf")
                        .accept(MediaType.APPLICATION_PDF))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=rendered.pdf"))
                .andExpect(content().bytes(expected));
    }

    @Test
    void render_missingTemplatePart_returns400() throws Exception {
        var service = mock(RenderService.class);
        mockMvc(service)
                .perform(multipart("/api/v1/render")
                        .file(new MockMultipartFile("data", "", MediaType.APPLICATION_JSON_VALUE,
                                "{\"name\":\"x\"}".getBytes()))
                        .param("target", "pdf"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void render_missingDataPart_returns400() throws Exception {
        var service = mock(RenderService.class);
        mockMvc(service)
                .perform(multipart("/api/v1/render")
                        .file(new MockMultipartFile("template", "t.docx",
                                "application/octet-stream", "fake".getBytes()))
                        .param("target", "pdf"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void render_malformedDataJson_returns400() throws Exception {
        var service = mock(RenderService.class);
        mockMvc(service)
                .perform(multipart("/api/v1/render")
                        .file(new MockMultipartFile("template", "t.docx",
                                "application/octet-stream", "fake".getBytes()))
                        .file(new MockMultipartFile("data", "",
                                MediaType.APPLICATION_JSON_VALUE, "not-json".getBytes()))
                        .param("target", "pdf"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void render_renderFailed_returns422() throws Exception {
        var service = mock(RenderService.class);
        when(service.renderToPdf(any(InputStream.class), anyLong(), any()))
                .thenThrow(new ConversionException(ConversionException.Reason.RENDER_FAILED));

        mockMvc(service)
                .perform(multipart("/api/v1/render")
                        .file(new MockMultipartFile("template", "t.docx",
                                "application/octet-stream", "fake".getBytes()))
                        .file(new MockMultipartFile("data", "",
                                MediaType.APPLICATION_JSON_VALUE, "{}".getBytes()))
                        .param("target", "pdf"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("render_failed"));
    }

    @Test
    void render_payloadTooLarge_returns413() throws Exception {
        var service = mock(RenderService.class);
        when(service.renderToPdf(any(InputStream.class), anyLong(), any()))
                .thenThrow(new ConversionException(ConversionException.Reason.PAYLOAD_TOO_LARGE));

        mockMvc(service)
                .perform(multipart("/api/v1/render")
                        .file(new MockMultipartFile("template", "t.docx",
                                "application/octet-stream", "fake".getBytes()))
                        .file(new MockMultipartFile("data", "",
                                MediaType.APPLICATION_JSON_VALUE, "{}".getBytes()))
                        .param("target", "pdf"))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code").value("payload_too_large"));
    }

    private MockMvc mockMvc(RenderService service) {
        return MockMvcBuilders.standaloneSetup(new RenderController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }
}
