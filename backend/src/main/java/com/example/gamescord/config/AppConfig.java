package com.example.gamescord.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class AppConfig {

    // 타임아웃이 없으면 외부 서버가 응답하지 않을 때 요청 스레드가 무한정 기다린다
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder,
                                     @Value("${http.client.connect-timeout:3s}") Duration connectTimeout,
                                     @Value("${http.client.read-timeout:30s}") Duration readTimeout) {
        return builder
                .setConnectTimeout(connectTimeout)
                .setReadTimeout(readTimeout)
                .build();
    }
}
