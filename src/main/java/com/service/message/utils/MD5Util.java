package com.service.message.utils;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * @Author: ninth-sun
 * @Date: 2025/12/11 16:00
 * @Description: MD5生成工具类
 */
public class MD5Util {
    /**
     * 生成字符串的MD5值（32位小写）
     * @param input 待加密字符串
     * @return 32位小写MD5摘要，失败返回空字符串
     */
    public static String generateMD5(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        try {
            // 初始化MD5算法
            MessageDigest md = MessageDigest.getInstance("MD5");
            // 将字符串转为字节数组并计算哈希
            byte[] md5Bytes = md.digest(input.getBytes());
            // 将字节数组转为32位十六进制字符串（小写）
            BigInteger bigInt = new BigInteger(1, md5Bytes);
            String md5Str = bigInt.toString(16);
            // 补全前导0（确保32位）
            while (md5Str.length() < 32) {
                md5Str = "0" + md5Str;
            }
            return md5Str;
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return "";
        }
    }

    // 测试示例
    public static void main(String[] args) {
        String testStr = "prod";
        String md5 = generateMD5(testStr);
        System.out.println("原始字符串：" + testStr);
        System.out.println("MD5值（32位小写）：" + md5);
        // 输出示例：5f4dcc3b5aa765d61d8327deb882cf99（若输入为"password"）
    }
}
