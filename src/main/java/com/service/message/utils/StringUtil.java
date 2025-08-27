package com.service.message.utils;

/**
 * @Author: ninth-sun
 * @Date: 2024/12/18 13:53
 * @Description: 字符串工具类
 */
public class StringUtil {

    public static boolean isEmpty(Object obj) {
        return obj == null || obj.toString().isEmpty();
    }

    public static boolean isNotEmpty(Object obj) {
        return !isEmpty(obj);
    }
}
