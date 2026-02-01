package com.skydawn.common.crypto;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;

/**
 * Argon2 密码加密工具类
 */
public final class Argon2Crypto {

    private static final Argon2 argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id);

    // Argon2 参数配置
    private static final int ITERATIONS = 3;      // 迭代次数
    private static final int MEMORY = 65536;      // 内存使用 64MB
    private static final int PARALLELISM = 1;     // 并行度

    private Argon2Crypto() {
        // 工具类，禁止实例化
    }

    /**
     * 加密密码
     *
     * @param plainPassword 明文密码
     * @return 加密后的密码哈希
     */
    public static String hashPassword(String plainPassword) {
        if (plainPassword == null || plainPassword.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }
        try {
            return argon2.hash(ITERATIONS, MEMORY, PARALLELISM, plainPassword.toCharArray());
        } finally {
            // 清除内存中的密码
            argon2.wipeArray(plainPassword.toCharArray());
        }
    }

    /**
     * 验证密码
     *
     * @param hashedPassword 数据库中存储的密码哈希
     * @param plainPassword  用户输入的明文密码
     * @return 密码是否匹配
     */
    public static boolean verifyPassword(String hashedPassword, String plainPassword) {
        if (hashedPassword == null || plainPassword == null) {
            return false;
        }
        try {
            return argon2.verify(hashedPassword, plainPassword.toCharArray());
        } finally {
            argon2.wipeArray(plainPassword.toCharArray());
        }
    }
}
