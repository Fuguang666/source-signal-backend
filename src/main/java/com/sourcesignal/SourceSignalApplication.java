package com.sourcesignal;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * SourceSignal 采购信号 - 应用启动类
 *
 * 订阅制跨境采购线索 SaaS
 * 实时采集 Reddit 采购需求，AI 打标分级，5 分钟内推送
 */
@SpringBootApplication
@EnableScheduling
@MapperScan("com.sourcesignal.mapper")
public class SourceSignalApplication {

    public static void main(String[] args) {
        SpringApplication.run(SourceSignalApplication.class, args);
    }
}
