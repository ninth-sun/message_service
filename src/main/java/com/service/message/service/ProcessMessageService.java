package com.service.message.service;

import com.service.message.pojo.CheckReceive;

import java.util.List;

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

    /**
     * 生成采购需求申请流程
     * @param number
     * @return
     */
    void generateBuyRequestProcess(String number);

    /**
     * 定时生成采购需求申请流程
     */
    void generateBuyRequestProcessByTime();

    /**
     * 定时生成备货申请流程
     */
    void generateStockRequestProcessByTime();

    /**
     * 推送到货验收入库数据
     */
    void pushCheckReceiveProcess(List<CheckReceive> checkReceives);
}
