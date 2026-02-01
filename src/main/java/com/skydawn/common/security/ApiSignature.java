package com.skydawn.common.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * API 签名工具类
 * 使用 HMAC-SHA256 算法生成和验证签名
 */
public final class ApiSignature {

    private static final String ALGORITHM = "HmacSHA256";

    private ApiSignature() {
        // 工具类，禁止实例化
    }

    /**
     * 生成签名
     *
     * @param appId     应用ID
     * @param timestamp 时间戳（毫秒）
     * @param nonce     随机字符串
     * @param body      请求体
     * @param secretKey 密钥
     * @return 签名字符串（十六进制小写）
     */
    public static String generateSignature(String appId, String timestamp, String nonce,
                                           String body, String secretKey) {
        // 拼接签名字符串
        String signStr = buildSignString(appId, timestamp, nonce, body);
        return hmacSha256(signStr, secretKey);
    }

    /**
     * 验证签名
     *
     * @param appId           应用ID
     * @param timestamp       时间戳（毫秒）
     * @param nonce           随机字符串
     * @param body            请求体
     * @param secretKey       密钥
     * @param signature       待验证的签名
     * @return 签名是否有效
     */
    public static boolean verifySignature(String appId, String timestamp, String nonce,
                                          String body, String secretKey, String signature) {
        String expectedSignature = generateSignature(appId, timestamp, nonce, body, secretKey);
        // 使用常量时间比较，防止时序攻击
        return constantTimeEquals(expectedSignature, signature);
    }

    /**
     * 构建签名字符串
     */
    public static String buildSignString(String appId, String timestamp, String nonce, String body) {
        StringBuilder sb = new StringBuilder();
        sb.append(appId != null ? appId : "");
        sb.append(timestamp != null ? timestamp : "");
        sb.append(nonce != null ? nonce : "");
        sb.append(body != null ? body : "");
        return sb.toString();
    }

    /**
     * HMAC-SHA256 签名
     *
     * @param data      待签名数据
     * @param secretKey 密钥
     * @return 签名结果（十六进制小写）
     */
    public static String hmacSha256(String data, String secretKey) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(
                    secretKey.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            mac.init(keySpec);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("HMAC-SHA256 签名失败", e);
        }
    }

    /**
     * 常量时间字符串比较（防止时序攻击）
     */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
