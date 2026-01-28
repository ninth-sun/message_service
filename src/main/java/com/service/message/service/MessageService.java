package com.service.message.service;

import java.util.List;

/**
 * @Author: ninth-sun
 * @Date: 2025/6/18 15:56
 * @Description: 消息服务接口
 */
public interface MessageService {

    void send(String ticketId);

    void sendCustomMessage(List<String> userAccounts, String msgtype, String content);

}
