package com.service.message.service;

/**
 * @Author: ninth-sun
 * @Date: 2025/11/21 15:30
 * @Description: 定时流程消息接口
 */
public interface TimeProcessMessageService {


    /**
     * 定时触发备品备件补充数量流程
     */
    void triggerUpdateNumProcess();

    /**
     * 定时触发预留延期流程
     */
    void triggerReservedDelayProcess(Integer day);

    /**
     * 定时触发设备(他方物权)到期提醒流程
     */
    void triggerOthersExpireProcess();
}
