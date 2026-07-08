package com.spring.transfer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class SpringTransferApplication {
    public static void main(String[] args) {
        SpringApplication.run(SpringTransferApplication.class, args);
    }
}
