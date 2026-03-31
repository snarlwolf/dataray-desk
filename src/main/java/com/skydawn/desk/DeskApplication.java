package com.skydawn.desk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.skydawn.desk.config.DeskPresenceProperties;

@SpringBootApplication
@ComponentScan(basePackages = "com.skydawn")
@EnableScheduling
@EnableConfigurationProperties(DeskPresenceProperties.class)
public class DeskApplication {
    public static void main(String[] args) {
        SpringApplication.run(DeskApplication.class, args);
    }
}