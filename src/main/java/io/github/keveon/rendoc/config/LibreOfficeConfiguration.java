package io.github.keveon.rendoc.config;

import java.io.IOException;
import java.nio.file.Files;
import org.jodconverter.core.DocumentConverter;
import org.jodconverter.core.office.OfficeManager;
import org.jodconverter.local.LocalConverter;
import org.jodconverter.local.office.LocalOfficeManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class LibreOfficeConfiguration {

    @Bean(initMethod = "start", destroyMethod = "stop")
    OfficeManager officeManager(LibreOfficeProperties properties) throws IOException {
        Files.createDirectories(properties.workingDirectory());

        var builder = LocalOfficeManager.builder()
                .workingDir(properties.workingDirectory().toFile())
                .portNumbers(properties.port())
                .maxTasksPerProcess(properties.maxTasksPerProcess())
                .processTimeout(properties.processTimeout().toMillis())
                .taskQueueTimeout(properties.taskQueueTimeout().toMillis())
                .taskExecutionTimeout(properties.taskExecutionTimeout().toMillis());

        if (properties.officeHome() != null && !properties.officeHome().isBlank()) {
            builder.officeHome(properties.officeHome());
        }
        return builder.build();
    }

    @Bean
    DocumentConverter documentConverter(OfficeManager officeManager) {
        return LocalConverter.make(officeManager);
    }
}
