package com.mindman;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * MindMan 心理健康助手启动类。
 *
 * <p>{@code @EnableScheduling} 启用定时任务（用于实时心理文章爬取）。</p>
 * <p>{@code @EnableAsync} 启用异步任务（爬取与 AI 同步调用隔离）。</p>
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
public class MindManApplication {
    public static void main(String[] args) {
        SpringApplication.run(MindManApplication.class, args);
    }
}
