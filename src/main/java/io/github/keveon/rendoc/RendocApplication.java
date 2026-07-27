package io.github.keveon.rendoc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RendocApplication {

    public static void main(String[] args) {
        SpringApplication.run(RendocApplication.class, args);
    }
}
