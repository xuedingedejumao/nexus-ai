package com.example.nexusai;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.security.Security;

@SpringBootApplication
@MapperScan("com.example.nexusai.mapper")
public class NexusAiApplication {
    public static void main(String[] args) {
        SpringApplication.run(NexusAiApplication.class, args);
    }
}
