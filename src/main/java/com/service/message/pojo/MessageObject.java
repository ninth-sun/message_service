package com.service.message.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @Author: ninth-sun
 * @Date: 2025/12/18 17:11
 * @Description: 自定义消息对象
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageObject {

    /**
     * 用户账号列表
     */
    private List<String> userAccounts;

    /**
     * 消息类型(如:textcard)
     */
    private String msgtype;

    /**
     * 消息内容
     */
    private String content;

}
