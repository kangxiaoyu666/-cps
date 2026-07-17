package com.waimaicps;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class WaimaiCpsApplication {
    public static void main(String[] args) {
        SpringApplication.run(WaimaiCpsApplication.class, args);
    }
}
