package com.service.message.utils;

import java.util.Iterator;
import java.util.Map;

/**
 * @Author: ninth-sun
 * @Date: 2025/10/18 10:39
 * @Description:
 */
public class UrlUtil {
    private static final String CHARSET_UTF8 = "UTF-8";

    public UrlUtil() {
    }

    public static String generateQueryString(Map<String, String> params) {
        StringBuilder canonicalizedQueryString = new StringBuilder();
        Iterator var2 = params.entrySet().iterator();

        while(var2.hasNext()) {
            Map.Entry<String, String> entry = (Map.Entry)var2.next();
            canonicalizedQueryString.append((String)entry.getKey()).append("=").append((String)entry.getValue()).append("&");
        }

        if (canonicalizedQueryString.length() > 1) {
            canonicalizedQueryString.setLength(canonicalizedQueryString.length() - 1);
        }

        return canonicalizedQueryString.toString();
    }



}
