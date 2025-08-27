package com.service.message.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Map;

/**
 * @Author: ninth-sun
 * @Date: 2025/6/18 16:33
 * @Description: http请求工具类
 */
@Slf4j
public class HttpUtil {

    /**
     * 发送post请求
     *
     * @param url
     * @param requestBody
     * @return
     */
    public static String doRequestPost(String url, String requestBody) {
        RestTemplate restTemplate = new RestTemplate();
        // 创建请求头
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        // 将请求头和请求体封装到HttpEntity中
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                String.class
        );
        log.info("请求地址：{}，请求参数：{}，响应结果：{}", url, requestBody, response.getBody());
        return response.getBody();
    }

    public static String doRequestGet(String url, String params) {
        RestTemplate restTemplate = new RestTemplate();
        // 创建请求头
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        if (StringUtil.isNotEmpty(params)) url = url + "?" + params;
        URI uri = null;
        try {
            uri = new URI(url);
        } catch (Exception e) {
            e.printStackTrace();
        }
        // 将请求头封装到HttpEntity中
        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(
                uri,
                HttpMethod.GET,
                entity,
                String.class
        );
        log.info("请求地址：{}，请求参数：{}，响应结果：{}", url, params, response.getBody());
        return response.getBody();
    }

    public static String doRequestGet(String url, String params, Map<String, String> headerMap) {
        RestTemplate restTemplate = new RestTemplate();
        // 创建请求头
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        if (headerMap != null) {
            headerMap.forEach((key, value) -> {
                headers.set(key, value);
            });
        }
        if (StringUtil.isNotEmpty(params)) url = url + "?" + params;
        URI uri = null;
        try {
            uri = new URI(url);
        } catch (Exception e) {
            e.printStackTrace();
        }
        // 将请求头封装到HttpEntity中
        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(
                uri,
                HttpMethod.GET,
                entity,
                String.class
        );
        log.info("请求地址：{}，请求参数：{}，响应结果：{}", url, params, response.getBody());
        return response.getBody();
    }

}
