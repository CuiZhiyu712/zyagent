package com.zyagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class ZyagentApplication {
    public static void main(String[] args) {
        SpringApplication.run(ZyagentApplication.class, args);
    }
}
