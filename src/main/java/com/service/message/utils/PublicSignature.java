package com.service.message.utils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.Map;
import java.util.TreeMap;

/**
 * @Author: ninth-sun
 * @Date: 2025/10/18 10:38
 * @Description:
 */
public class PublicSignature {
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

        String headerString = UrlUtil.generateQueryString( sortHeader);

        String paramString = UrlUtil.generateQueryString(sortParameter);
        if (null == httpMethod) {
            throw new RuntimeException("httpMethod can not be empty");
        }
        // 构建待签名的字符串
        StringBuilder stringToSign = new StringBuilder();
        stringToSign.append(httpMethod).append("&").append(percentEncode(SEPARATOR)).append("&");
        stringToSign.append(percentEncode(headerString));
        if(StringUtil.isNotEmpty(paramString)){
            stringToSign.append("&").append(percentEncode(paramString));
        }
        if ((httpMethod.equals(HTTP.POST.method())
                || httpMethod.equals(HTTP.PUT.method())
                || httpMethod.equals(HTTP.DELETE.method())
                || httpMethod.equals(HTTP.PATCH.method()))
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

    public static enum HTTP {
        GET("GET"),
        POST("POST"),
        PUT("PUT"),
        DELETE("DELETE"),
        HEAD("HEAD"),
        PATCH("PATCH"),
        OPTIONS("OPTIONS"),
        TRACE("TRACE");

        private final String method;

        private HTTP(String method) {
            this.method = method;
        }

        public static HTTP validate(String method) {
            HTTP[] var1 = values();
            int var2 = var1.length;

            for(int var3 = 0; var3 < var2; ++var3) {
                HTTP m = var1[var3];
                if (m.method().equals(method)) {
                    return m;
                }
            }

            throw new IllegalArgumentException("invalid http method - " + method);
        }

        public String method() {
            return this.method;
        }
    }
}
