package io.github.keveon.rendoc.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.jodconverter.core.office.OfficeManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;

class LibreOfficeHealthIndicatorTest {

    @Test
    void reportsUpOnlyWhenTheOfficeManagerIsRunning() {
        var officeManager = mock(OfficeManager.class);
        when(officeManager.isRunning()).thenReturn(true);

        var health = new LibreOfficeHealthIndicator(officeManager).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void reportsDownWhenTheOfficeManagerIsStopped() {
        var officeManager = mock(OfficeManager.class);
        when(officeManager.isRunning()).thenReturn(false);

        var health = new LibreOfficeHealthIndicator(officeManager).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }
}
