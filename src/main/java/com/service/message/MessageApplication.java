package com.service.message;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@Configuration
@EnableTransactionManagement
@EnableScheduling
public class MessageApplication {

    public static void main(String[] args) {
        // 记录启动时间
        long startTime = System.currentTimeMillis();
        SpringApplication.run(MessageApplication.class, args);
        // 记录结束时间
        long endTime = System.currentTimeMillis();
        System.out.println("消息发送服务 应用程序启动耗时：" + (endTime - startTime) + "ms");
    }

}
