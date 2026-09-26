package com.sourcesignal.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * WebClient 配置（用于 Reddit API、AI 接口等外部 HTTP 调用）
 * 配置连接超时、读取超时、写入超时，避免网络不稳定时请求挂起
 */
@Configuration
public class WebClientConfig {

    /** 连接超时：10秒 */
    private static final int CONNECT_TIMEOUT_SECONDS = 10;
    /** 读取超时：30秒（Reddit API 响应较慢，limit=50时约需5-8秒） */
    private static final int READ_TIMEOUT_SECONDS = 60;
    /** 写入超时：10秒 */
    private static final int WRITE_TIMEOUT_SECONDS = 10;

    @Bean
    public WebClient.Builder webClientBuilder() {
        HttpClient httpClient = HttpClient.newConnection()  // 每次请求使用新连接，避免连接池复用异常连接导致I/O中断
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_SECONDS * 1000)
                .responseTimeout(Duration.ofSeconds(READ_TIMEOUT_SECONDS)) // Reddit API 某些关键词响应较慢
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                );

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("User-Agent", "SourceSignal/1.0 (SaaS Procurement Intelligence)");
    }
}
