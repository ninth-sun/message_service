package com.service.message.controller;

import com.service.message.dto.WorkFlowCounterDTO;
import com.service.message.pojo.CommonResponse;
import com.service.message.service.BusinessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * @Author: ninth-sun
 * @Date: 2026/1/28 17:54
 * @Description:
 */
@RestController
@RequestMapping("/business")
public class BusinessController {

    @Autowired
    private BusinessService businessService;

    /**
     * 初始化分布式计数器
     */
    @PostMapping("/workflow/init")
    public CommonResponse initCounter(@RequestBody WorkFlowCounterDTO dto) {
        businessService.initCounter(dto);
        return CommonResponse.success();
    }

    /**
     * 更新分布式计数器并判断是否触发提交流程
     */
    @PostMapping("/workflow/update")
    public CommonResponse updateCounter(@RequestBody WorkFlowCounterDTO dto) {
        businessService.updateCounter(dto);
        return CommonResponse.success();
    }

    /**
     * 查询分布式计数器
     */
    @PostMapping("/workflow/get/key")
    public CommonResponse getKeyCounter(@RequestBody WorkFlowCounterDTO dto) {
        return CommonResponse.success(businessService.getKeyCounter(dto));
    }

    @GetMapping(value = "/sync/sap/single")
    public CommonResponse singleSync() {
        businessService.syncSapSingleTime();
        return CommonResponse.success();
    }

    @GetMapping(value = "/workflow/delete/key")
    public CommonResponse deleteKey(@RequestParam("key") String key) {
        businessService.deleteKey(key);
        return CommonResponse.success();
    }

    @GetMapping("/get/redis/value")
    public CommonResponse getRedisValueByKey(@RequestParam("key") String key) {
        return CommonResponse.success(businessService.getRedisValueByKey(key));
    }

    @GetMapping(value = "/time/trigger/sync/asset")
    public CommonResponse triggerSyncAsset() {
        businessService.timeTriggerSyncAsset(null, null);
        return CommonResponse.success();
    }

    @GetMapping(value = "/time/trigger/sync/asset/bytime")
    public CommonResponse triggerSyncAssetByTime(@RequestParam("startTime") String startTime, @RequestParam("endTime") String endTime) {
        businessService.timeTriggerSyncAsset(startTime, endTime);
        return CommonResponse.success();
    }

    @GetMapping(value = "/full/sync/asset")
    public CommonResponse syncFullAsset() {
        businessService.syncFullAsset();
        return CommonResponse.success();
    }

    @GetMapping(value = "/time/trigger/sync/sense/asset")
    public CommonResponse triggerSyncSenseCoreAsset() {
        businessService.timeTriggerSyncSenseCoreAsset(null, null);
        return CommonResponse.success();
    }

    @GetMapping(value = "/time/trigger/sync/sense/asset/bytime")
    public CommonResponse triggerSyncSenseCoreAssetByTime(@RequestParam("startTime") String startTime, @RequestParam("endTime") String endTime) {
        businessService.timeTriggerSyncSenseCoreAsset(startTime, endTime);
        return CommonResponse.success();
    }

    @GetMapping(value = "/full/sync/sense/asset")
    public CommonResponse syncFullSenseCoreAsset() {
        businessService.syncFullSenseCoreAsset();
        return CommonResponse.success();
    }

}
