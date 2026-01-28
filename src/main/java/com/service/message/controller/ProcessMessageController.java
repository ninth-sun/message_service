package com.service.message.controller;

import com.service.message.constant.CommonConstant;
import com.service.message.pojo.CheckReceive;
import com.service.message.service.ProcessMessageService;
import com.service.message.service.TimeProcessMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @Author: ninth-sun
 * @Date: 2025/6/18 15:55
 * @Description:
 */
@RestController
@RequestMapping("/process/message")
public class ProcessMessageController {

    @Autowired
    private ProcessMessageService processMessageService;

    @Autowired
    private TimeProcessMessageService timeProcessMessageService;

    @GetMapping(value = "/buy/request")
    public String generateBuyRequestProcess(@RequestParam(name = "number", required = false) String number) {
        processMessageService.generateBuyRequestProcess(number);
        return CommonConstant.SUCCESS;
    }

    @GetMapping(value = "/time/request")
    public String generateBuyRequestProcess() {
        processMessageService.generateBuyRequestProcessByTime();
        processMessageService.generateStockRequestProcessByTime();
        return CommonConstant.SUCCESS;
    }

    @GetMapping(value = "/check/receive")
    public String generateCheckReceiveProcess(@RequestParam(name = "number", required = false) String number) {
        return processMessageService.generateCheckReceiveProcess(number);
    }

    @PostMapping(value = "/push/receive")
    public String pushCheckReceiveProcess(@RequestBody List<CheckReceive> checkReceives) {
        processMessageService.pushCheckReceiveProcess(checkReceives);
        return CommonConstant.SUCCESS;
    }

    @GetMapping(value = "/time/trigger/reserved/delay")
    public String triggerReservedDelayProcess(@RequestParam(name = "day", required = false) Integer day) {
        timeProcessMessageService.triggerReservedDelayProcess(day);
        return CommonConstant.SUCCESS;
    }

    @GetMapping(value = "/time/trigger/update/num")
    public String triggerUpdateNumProcess() {
        timeProcessMessageService.triggerUpdateNumProcess();
        return CommonConstant.SUCCESS;
    }

    @GetMapping(value = "/time/trigger/others/expire")
    public String triggerOthersExpireProcess() {
        timeProcessMessageService.triggerOthersExpireProcess();
        return CommonConstant.SUCCESS;
    }

}
