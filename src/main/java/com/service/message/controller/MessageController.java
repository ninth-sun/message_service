package com.service.message.controller;

import com.service.message.constant.CommonConstant;
import com.service.message.pojo.MessageObject;
import com.service.message.service.MessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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
    public String sendMessageByTicketId(@RequestParam("ticket_id") String ticketId) {
        messageService.send(ticketId);
        return CommonConstant.SUCCESS;
    }

    @PostMapping(value = "/send/custom")
    public String sendMessage(@RequestBody MessageObject messageObject) {
        messageService.sendCustomMessage(messageObject.getUserAccounts(), messageObject.getMsgtype(), messageObject.getContent());
        return CommonConstant.SUCCESS;
    }

    @GetMapping(value = "/test")
    public String test() {
        try {
            // 休眠5分钟（300秒 = 300 * 1000 毫秒）
            // 注意：单位是毫秒，所以300秒要写成 300 * 1000
            Thread.sleep(300 * 1000);
        } catch (InterruptedException e) {
            // 捕获线程中断异常，避免程序报错
            Thread.currentThread().interrupt(); // 恢复中断状态
            return "INTERRUPTED"; // 可选：返回中断提示
        }
        return CommonConstant.SUCCESS;
    }

}
