package com.service.message;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
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
