package io.github.keveon.rendoc.conversion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class SourceFormatTest {

    @Test
    void parsesSupportedFormatsWithoutDependingOnCase() {
        assertThat(SourceFormat.parse("docx")).isEqualTo(SourceFormat.DOCX);
        assertThat(SourceFormat.parse("XLSX")).isEqualTo(SourceFormat.XLSX);
    }

    @Test
    void rejectsUnsupportedSourceFormats() {
        assertThatThrownBy(() -> SourceFormat.parse("pdf"))
                .isInstanceOf(ConversionException.class)
                .extracting(error -> ((ConversionException) error).reason())
                .isEqualTo(ConversionException.Reason.UNSUPPORTED_FORMAT);
    }

    @Test
    void acceptsTheDeclaredMimeTypeAndGenericBinaryOnly() {
        assertThat(SourceFormat.DOCX.accepts(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))).isTrue();
        assertThat(SourceFormat.DOCX.accepts(MediaType.APPLICATION_OCTET_STREAM)).isTrue();
        assertThat(SourceFormat.DOCX.accepts(MediaType.APPLICATION_PDF)).isFalse();
    }
}
