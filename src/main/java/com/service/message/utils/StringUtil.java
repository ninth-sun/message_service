package com.service.message.utils;

import lombok.extern.slf4j.Slf4j;

/**
 * @Author: ninth-sun
 * @Date: 2024/12/18 13:53
 * @Description: 字符串工具类
 */
@Slf4j
public class StringUtil {

    public static boolean isEmpty(Object obj) {
        return obj == null || obj.toString().isEmpty();
    }

    public static boolean isNotEmpty(Object obj) {
        return !isEmpty(obj);
    }

    public static void main(String[] args) {
        System.out.println("控制台日志111");
        log.debug("debug111");
        log.info("info111");
        log.error("error111");
        log.warn("warn111");
    }
}
