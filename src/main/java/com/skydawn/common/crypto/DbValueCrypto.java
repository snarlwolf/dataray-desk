package com.skydawn.common.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public final class DbValueCrypto {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LEN = 12;        // GCM 推荐 12 bytes
    private static final int TAG_LEN_BIT = 128;  // 16 bytes tag

    private static final SecureRandom RANDOM = new SecureRandom();

    private DbValueCrypto() {}

    /**
     * 加密：输出 Base64( IV(12) + CIPHERTEXT+TAG )
     */
    public static String encryptToBase64(String token, String plain) {
        try {
            if (token == null || token.isBlank()) {
                throw new IllegalArgumentException("token is blank");
            }
            if (plain == null) {
                throw new IllegalArgumentException("plain is null");
            }

            byte[] key32 = sha256(token); // 32 bytes -> AES-256 key
            SecretKeySpec key = new SecretKeySpec(key32, "AES");

            byte[] iv = new byte[IV_LEN];
            RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LEN_BIT, iv));

            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));

            ByteBuffer bb = ByteBuffer.allocate(iv.length + ct.length);
            bb.put(iv);
            bb.put(ct);

            return Base64.getEncoder().encodeToString(bb.array());
        } catch (Exception e) {
            throw new RuntimeException("encrypt failed", e);
        }
    }

    /**
     * 解密：输入 Base64( IV(12) + CIPHERTEXT+TAG )
     */
    public static String decryptFromBase64(String token, String cipherB64) {
        try {
            if (token == null || token.isBlank()) {
                throw new IllegalArgumentException("token is blank");
            }
            if (cipherB64 == null || cipherB64.isBlank()) {
                throw new IllegalArgumentException("cipherB64 is blank");
            }

            byte[] all = Base64.getDecoder().decode(cipherB64);
            if (all.length <= IV_LEN) {
                throw new IllegalArgumentException("bad ciphertext");
            }

            byte[] iv = new byte[IV_LEN];
            byte[] ct = new byte[all.length - IV_LEN];
            System.arraycopy(all, 0, iv, 0, IV_LEN);
            System.arraycopy(all, IV_LEN, ct, 0, ct.length);

            byte[] key32 = sha256(token);
            SecretKeySpec key = new SecretKeySpec(key32, "AES");

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LEN_BIT, iv));

            byte[] pt = cipher.doFinal(ct);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("decrypt failed", e);
        }
    }

    private static byte[] sha256(String s) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return md.digest(s.getBytes(StandardCharsets.UTF_8));
    }
    
    /**
     * 计算消息内容的指纹（用于去重）
     * 使用 SHA-256 算法计算哈希值，然后进行 Base64 编码
     * 输出长度：44 字符（32字节 SHA-256 -> Base64编码）
     * 
     * @param messageContent 消息内容
     * @return Base64 编码的 SHA-256 哈希值（44字符）
     * @throws IllegalArgumentException 如果消息内容为 null
     */
    public static String computeMessageFingerprint(String messageContent) {
        if (messageContent == null) {
            throw new IllegalArgumentException("messageContent is null");
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(messageContent.getBytes(StandardCharsets.UTF_8));
            // Base64 编码：32字节 -> 44字符（32 * 4/3 = 42.67，向上取整44）
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("计算消息指纹失败", e);
        }
    }
}
