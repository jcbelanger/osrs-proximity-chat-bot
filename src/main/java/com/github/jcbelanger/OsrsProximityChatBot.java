package com.github.jcbelanger;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@Slf4j
public class OsrsProximityChatBot {

    public static void main(String[] args) {
        new SpringApplicationBuilder(OsrsProximityChatBot.class)
                .web(WebApplicationType.NONE)
                .run(args);
    }

    @Bean
    ApplicationRunner botInitializer(OsrsProximityChatService osrsProximityChatService) {
        return args -> {
            log.info("proximity service started");
            osrsProximityChatService.proximityChat().block();
            log.info("proximity service finished");
        };
    }
}