package io.github.keveon.rendoc.health;

import org.jodconverter.core.office.OfficeManager;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("libreOffice")
public class LibreOfficeHealthIndicator implements HealthIndicator {

    private final OfficeManager officeManager;

    public LibreOfficeHealthIndicator(OfficeManager officeManager) {
        this.officeManager = officeManager;
    }

    @Override
    public Health health() {
        if (officeManager.isRunning()) {
            return Health.up().withDetail("renderer", "ready").build();
        }
        return Health.down().withDetail("renderer", "stopped").build();
    }
}
