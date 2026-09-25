package com.sourcesignal.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * WebClient 配置（用于 Reddit API、AI 接口等外部 HTTP 调用）
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder()
                .defaultHeader("User-Agent", "SourceSignal/1.0 (SaaS Procurement Intelligence)");
    }
}
