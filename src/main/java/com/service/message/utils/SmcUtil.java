package com.service.message.utils;

import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * @Author: ninth-sun
 * @Date: 2025/10/19 16:19
 * @Description:
 */
@Slf4j
public class SmcUtil {

    public static void main(String[] args) throws Exception {
        String ak = "00000000000000183819304608989259";
        String sk = "0000000000000018381930460898926030460898";
        // get请求
        String getUrl = "https://tenon-cmdb-api-test.sensetime.com/custom/serviceapi/v1/test";
        System.out.println(smcSendGet(getUrl, ak, sk, new TreeMap<>()));

        // post请求
        String url = "https://tenon-cmdb-api-test.sensetime.com/custom/serviceapi/v1/third/pda/receive";
        JSONObject bodyJson = new JSONObject();
        bodyJson.put("c_endTime", "2023-03-30 20:01:30");
        bodyJson.put("c_startTime", "2023-03-30 00:00:00");
        bodyJson.put("sta_num", "54597");
        System.out.println(smcSendPost(url, ak, sk, new TreeMap<>(), bodyJson.toString()));
    }

    /**
     * @param url      请求的url
     * @param ak       ak
     * @param sk       sk
     * @param paramMap url中的参数，给一个map
     * @param jsonBody 请求体
     * @throws Exception
     */
    public static String smcSendPost(String url, String ak, String sk, TreeMap<String, String> paramMap, String jsonBody) {
        String uuid = UUID.randomUUID().toString();
        String timeStamp = String.valueOf(System.currentTimeMillis() / 1000);

        // 请求头
        TreeMap<String, String> headerMap = new TreeMap();
        headerMap.put("x-smc-appkey", ak);             // ak
        headerMap.put("x-smc-nonce", uuid);           // 五分钟内的重放值
        headerMap.put("x-smc-timestamp", timeStamp);  // 时间戳秒级

        String strBeforeSign = getStrBeforeSign(
                "POST",
                paramMap,
                headerMap,
                jsonBody
        );
        // 暂时未加密的sign值
        System.out.println("strBeforeSign: " + strBeforeSign);
        // 根据sk进行加密，生成sign
        String sign = null;
        try {
            sign = generate(
                    "POST",
                    paramMap,
                    headerMap,
                    sk,
                    jsonBody
            );
            System.out.println("sign: " + sign);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        // 生成参数url
        String paramUrl = generateQueryString(paramMap);
        url = url + (StringUtil.isEmpty(paramUrl) ? "" : "?" + paramUrl);
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Smc-Appkey", ak);
        headers.add("X-Smc-Nonce", uuid);
        headers.add("X-Smc-Signature", sign);
        headers.add("X-Smc-Timestamp", timeStamp);
        headers.setContentType(MediaType.APPLICATION_JSON); // 设置请求正文的媒体类型为 JSON
        HttpEntity<String> requestEntity = new HttpEntity<>(jsonBody, headers);
        ResponseEntity<String> responseEntity = restTemplate.exchange(
                url,
                HttpMethod.POST, // 使用 POST 方法
                requestEntity,
                String.class
        );
        String body = responseEntity.getBody();
        log.info("请求地址：{}，请求参数：{}，响应结果：{}", url, jsonBody, body);
        return body;
    }

    public static String smcSendGet(String url, String ak, String sk, TreeMap<String, String> paramMap) {
        String uuid = UUID.randomUUID().toString();
        String timeStamp = String.valueOf(System.currentTimeMillis() / 1000);

        // 请求头
        TreeMap<String, String> headerMap = new TreeMap();
        headerMap.put("x-smc-appkey", ak);             // ak
        headerMap.put("x-smc-nonce", uuid);           // 五分钟内的重放值
        headerMap.put("x-smc-timestamp", timeStamp);  // 时间戳秒级

        String strBeforeSign = getStrBeforeSign(
                "GET",
                paramMap,
                headerMap,
                null
        );
        // 暂时未加密的sign值
        System.out.println("strBeforeSign: " + strBeforeSign);
        // 根据sk进行加密，生成sign
        String sign = null;
        try {
            sign = generate(
                    "GET",
                    paramMap,
                    headerMap,
                    sk,
                    null
            );
            System.out.println(sign);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // 生成参数url
        String paramUrl = generateQueryString(paramMap);
        url = url + (StringUtil.isEmpty(paramUrl) ? "" : "?" + paramUrl);
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Smc-Appkey", ak);
        headers.add("X-Smc-Nonce", uuid);
        headers.add("X-Smc-Signature", sign);
        headers.add("X-Smc-Timestamp", timeStamp);
        HttpEntity<String> requestEntity = new HttpEntity<>(headers);
        ResponseEntity<String> responseEntity = restTemplate.exchange(
                url,
                HttpMethod.GET,
                requestEntity,
                String.class
        );
        String body = responseEntity.getBody();
        log.info("请求地址：{}，请求参数：{}，响应结果：{}", paramUrl, paramMap, body);
        return body;
    }

    private final static String CHARSET_UTF8 = "utf8";
    private final static String ALGORITHM = "UTF-8";
    private final static String SEPARATOR = "/";


    public static String generate(String method, Map<String, String> parameter, Map<String, String> header,
                                  String accessKeySecret, String postString) throws Exception {
        String signString = generateSignString(method, parameter, header, postString);

        byte[] signBytes = hmacSHA256Signature(accessKeySecret, signString);
        String signature = byte2hex(signBytes);
        if ("POST".equals(method)) {
            return signature;
        }
        return URLEncoder.encode(signature, "UTF-8");
    }

    public static String getStrBeforeSign(String method, Map<String, String> parameter, Map<String, String> header,
                                          String postString) {
        String signString = generateSignString(method, parameter, header, postString);
        return signString;
    }

    public static String generateSignString(String httpMethod, Map<String, String> parameter,
                                            Map<String, String> header, String postString) {
        TreeMap<String, String> sortParameter = new TreeMap<String, String>();
        sortParameter.putAll(parameter);

        TreeMap<String, String> sortHeader = new TreeMap<String, String>();
        sortHeader.putAll(header);

        String headerString = generateQueryString(sortHeader);

        String paramString = generateQueryString(sortParameter);
        if (null == httpMethod) {
            throw new RuntimeException("httpMethod can not be empty");
        }
        // 构建待签名的字符串
        StringBuilder stringToSign = new StringBuilder();
        stringToSign.append(httpMethod).append("&").append(percentEncode(SEPARATOR)).append("&");
        stringToSign.append(percentEncode(headerString));
        if (StringUtil.isNotEmpty(paramString)) {
            stringToSign.append("&").append(percentEncode(paramString));
        }
        if ((httpMethod.equals("POST")
                || httpMethod.equals("PUT")
                || httpMethod.equals("DELETE")
                || httpMethod.equals("PATCH"))
                && !StringUtil.isEmpty(postString)) {
            stringToSign.append("&");
            stringToSign.append(percentEncode(postString));
        }
        return stringToSign.toString().toLowerCase();
    }

    public static byte[] hmacSHA256Signature(String secret, String baseString) throws Exception {
        if (isEmpty(secret)) {
            throw new IOException("secret can not be empty");
        }
        if (isEmpty(baseString)) {
            return null;
        }
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(CHARSET_UTF8), ALGORITHM);
        mac.init(keySpec);
        return mac.doFinal(baseString.getBytes(CHARSET_UTF8));
    }

    private static boolean isEmpty(String str) {
        return (str == null || str.length() == 0);
    }

    public static String byte2hex(byte[] b) {
        StringBuilder hs = new StringBuilder();
        String stmp;
        for (int n = 0; b != null && n < b.length; n++) {
            stmp = Integer.toHexString(b[n] & 0XFF);
            if (stmp.length() == 1) {
                hs.append('0');
            }
            hs.append(stmp);
        }
        return hs.toString();
    }

    public static String percentEncode(String value) {
        try {
            return value == null ? null
                    : URLEncoder.encode(value, CHARSET_UTF8).replace("+", "%20").replace("_", "%5F").replace("*", "%2A").replace("%7E",
                    "~");
        } catch (Exception e) {
        }
        return "";
    }

    public static String generateQueryString(Map<String, String> params) {
        StringBuilder canonicalizedQueryString = new StringBuilder();
        Iterator var2 = params.entrySet().iterator();

        while (var2.hasNext()) {
            Map.Entry<String, String> entry = (Map.Entry) var2.next();
            canonicalizedQueryString.append((String) entry.getKey()).append("=").append((String) entry.getValue()).append("&");
        }

        if (canonicalizedQueryString.length() > 1) {
            canonicalizedQueryString.setLength(canonicalizedQueryString.length() - 1);
        }

        return canonicalizedQueryString.toString();
    }

}
