package com.service.message.property;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @Author: ninth-sun
 * @Date: 2025/6/18 16:39
 * @Description: 企微消息配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "wechat")
public class WechatProperty {

    private String url;

    private String agentId;

    private String secret;

    private String corpId;
}
