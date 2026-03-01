package com.cryptointel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CryptoIntelApplication {

    public static void main(String[] args) {
        SpringApplication.run(CryptoIntelApplication.class, args);
    }
}
