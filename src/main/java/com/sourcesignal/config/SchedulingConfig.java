package com.sourcesignal.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 异步与定时任务配置
 */
@Configuration
@EnableScheduling
@EnableAsync
public class SchedulingConfig {
}
