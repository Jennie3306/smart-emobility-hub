package com.hcmut.emobility;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling   // bat @Scheduled cho cac FR non-interactive
public class EmobilityApplication {

    public static void main(String[] args) {
        // Windows tieng Viet dat mui gio JVM la "Asia/Saigon" - PostgreSQL 16 khong biet ten nay
        // (ten chuan la "Asia/Ho_Chi_Minh"), nen driver bi tu choi ngay luc ket noi:
        //   FATAL: invalid value for parameter "TimeZone": "Asia/Saigon"
        // Dat mui gio o day de may nao chay cung giong nhau. Phai dat TRUOC SpringApplication.run.
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));

        SpringApplication.run(EmobilityApplication.class, args);
    }
}