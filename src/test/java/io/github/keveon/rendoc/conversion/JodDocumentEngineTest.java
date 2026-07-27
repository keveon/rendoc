package io.github.keveon.rendoc.conversion;

import static org.assertj.core.api.Assertions.assertThat;

import org.jodconverter.core.office.OfficeException;
import org.junit.jupiter.api.Test;

class JodDocumentEngineTest {

    @Test
    void classifiesQueueExhaustionAsATimeout() {
        var error = new OfficeException("No office manager available after 30000 millisec");

        assertThat(JodDocumentEngine.reasonFor(error))
                .isEqualTo(ConversionException.Reason.TIMEOUT);
    }

    @Test
    void classifiesOfficeFailuresAsRendererUnavailable() {
        var error = new OfficeException("Office process exited unexpectedly");

        assertThat(JodDocumentEngine.reasonFor(error))
                .isEqualTo(ConversionException.Reason.RENDERER_UNAVAILABLE);
    }

    @Test
    void classifiesManagerStateRacesAsRendererUnavailable() {
        assertThat(JodDocumentEngine.reasonFor(new IllegalStateException("manager stopped")))
                .isEqualTo(ConversionException.Reason.RENDERER_UNAVAILABLE);
    }
}
