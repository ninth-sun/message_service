package com.service.message.property;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @Author: ninth-sun
 * @Date: 2025/7/18 15:35
 * @Description:
 */
@Data
@Component
@ConfigurationProperties(prefix = "scene-diy")
public class SceneDiyProperty {

    private String url;

    private String apiKey;

    private String oaUrl;

    private String oaPassword;

    private String token;

    private String smcUrl;

    private String smcAk;

    private String smcSk;

//    private String batchServiceId;

    private String env;
}
