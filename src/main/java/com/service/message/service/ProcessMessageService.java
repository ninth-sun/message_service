package com.service.message.service;

/**
 * @Author: ninth-sun
 * @Date: 2025/8/18 17:38
 * @Description: 流程消息接口
 */
public interface ProcessMessageService {

    /**
     * 生成到货验收入库流程
     * @param number
     * @return
     */
    String generateCheckReceiveProcess(String number);
}
