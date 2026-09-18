package com.hcmut.emobility;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling   // bat @Scheduled cho cac FR non-interactive
public class EmobilityApplication {
    public static void main(String[] args) {
        SpringApplication.run(EmobilityApplication.class, args);
    }
}
