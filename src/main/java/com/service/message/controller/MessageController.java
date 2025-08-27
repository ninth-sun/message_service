package com.service.message.controller;

import com.service.message.constant.CommonConstant;
import com.service.message.service.MessageService;
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
@RequestMapping("/message")
public class MessageController {

    @Autowired
    private MessageService messageService;

    @GetMapping(value = "/send")
    public String getCabinetResourcesDetail(@RequestParam("ticketId") String ticketId) {
        messageService.send(ticketId);
        return CommonConstant.SUCCESS;
    }

    @GetMapping(value = "/test")
    public String test() {
        return CommonConstant.SUCCESS;
    }

}
