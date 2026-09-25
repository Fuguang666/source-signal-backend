package com.sourcesignal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SourceSignal 采购信号 - 应用启动类
 *
 * 订阅制跨境采购线索 SaaS
 * 实时采集 Reddit 采购需求，AI 打标分级，5 分钟内推送
 */
@SpringBootApplication
public class SourceSignalApplication {

    public static void main(String[] args) {
        SpringApplication.run(SourceSignalApplication.class, args);
    }
}
