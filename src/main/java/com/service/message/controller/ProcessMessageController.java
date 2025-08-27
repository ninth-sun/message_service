package com.service.message.controller;

import com.service.message.service.ProcessMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    @GetMapping(value = "/buy/request")
    public String generateBuyRequestProcess(@RequestParam("number") String number) {
        return "success";
    }

    @GetMapping(value = "/check/receive")
    public String generateCheckReceiveProcess(@RequestParam("number") String number) {
        processMessageService.generateCheckReceiveProcess(number);
        return "success";
    }

}
