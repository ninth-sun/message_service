package com.service.message.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.util.*;

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

    public static String doRequestPost(String url, String requestBody, Map<String, String> headerMap) {
        RestTemplate restTemplate = new RestTemplate();
        // 创建请求头
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        if (headerMap != null) {
            headerMap.forEach((key, value) -> {
                headers.set(key, value);
            });
        }
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

    public static void main(String[] args) {

        // 设置日期范围：2025年6月
        int year = 2026;
        Month month = Month.JANUARY;

        // 生成6月份所有日期的时间列表
        List<String> timeList = generateJuneTimes(year, month);

        // 打印结果
        System.out.println("2025年6月随机打卡时间：");
        for (String time : timeList) {
            String str = "INSERT INTO user_clock_in_record VALUES('" + UUID.randomUUID() +"', '000139', '王添', '"+ time +"', '441671425', '园区西门考勤', 'DM', 2026, 1);";
            System.out.println(str);
        }

        System.out.println("打卡时间汇总表格已生成: ");
    }


    /**
     * 生成指定月份每天两个时间段的随机时间
     * @param year 年份
     * @param month 月份
     * @return 时间字符串列表
     */
    public static List<String> generateJuneTimes(int year, Month month) {
        List<String> times = new ArrayList<>();
        Random random = new Random();

        // 获取月份的天数
        int daysInMonth = month.length(LocalDate.of(year, 1, 1).isLeapYear());

        // 时间格式化器
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        // 遍历6月的每一天
        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate date = LocalDate.of(year, month, day);

            // 生成上午时间 (08:50-09:00)
            LocalTime morningTime = generateRandomTime(
                    LocalTime.of(8, 50),
                    LocalTime.of(9, 0),
                    random
            );

            // 生成下午时间 (18:00-18:10)
            LocalTime afternoonTime = generateRandomTime(
                    LocalTime.of(18, 5),
                    LocalTime.of(18, 10),
                    random
            );

            // 组合日期和时间
            LocalDateTime morningDateTime = LocalDateTime.of(date, morningTime);
            LocalDateTime afternoonDateTime = LocalDateTime.of(date, afternoonTime);

            // 添加到结果列表
            times.add(morningDateTime.format(formatter));
            times.add(afternoonDateTime.format(formatter));
        }

        return times;
    }

    /**
     * 生成指定时间范围内的随机时间
     * @param start 开始时间
     * @param end 结束时间
     * @param random 随机数生成器
     * @return 随机时间
     */
    private static LocalTime generateRandomTime(LocalTime start, LocalTime end, Random random) {
        // 计算总秒数范围
        int startSeconds = start.toSecondOfDay();
        int endSeconds = end.toSecondOfDay();

        // 生成随机秒数
        int randomSeconds = startSeconds + random.nextInt(endSeconds - startSeconds + 1);

        // 确保在范围内
        randomSeconds = Math.min(Math.max(randomSeconds, startSeconds), endSeconds);

        return LocalTime.ofSecondOfDay(randomSeconds);
    }

}
