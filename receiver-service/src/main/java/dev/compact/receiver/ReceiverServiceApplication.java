package dev.compact.receiver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ReceiverServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReceiverServiceApplication.class, args);
    }
}
