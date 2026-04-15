package com.service.message.service;

import com.service.message.dto.WorkFlowCounterDTO;

import java.util.Map;

/**
 * @Author: ninth-sun
 * @Date: 2026/1/28 17:58
 * @Description:
 */
public interface BusinessService {

    void initCounter(WorkFlowCounterDTO dto);

    void updateCounter(WorkFlowCounterDTO dto);

    Map<String, Object> getKeyCounter(WorkFlowCounterDTO dto);

    void syncSapSingleTime();

    void deleteKey(String key);

    void timeTriggerSyncAsset(String startTime, String endTime);

    Object getRedisValueByKey(String redisKey);

    void syncFullAsset();

    void timeTriggerSyncSenseCoreAsset(String startTime, String endTime);

    void syncFullSenseCoreAsset();
}
