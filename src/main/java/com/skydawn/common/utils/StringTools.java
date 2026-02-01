package com.skydawn.common.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 字符串工具类
 */
public final class StringTools {
    private static final Logger log = LoggerFactory.getLogger(StringTools.class);
    
    private StringTools() {}

    /**
     * 将字符串转换为整数
     * 如果转换失败或发生异常，返回默认值
     * 
     * @param value 要转换的字符串（可以为 null 或空字符串）
     * @param defaultValue 转换失败时返回的默认值
     * @return 转换后的整数值，如果转换失败则返回默认值
     */
    public static int parseInt(String value, int defaultValue) {
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            log.warn("字符串转换为整数失败，使用默认值 - 输入值: \"{}\", 默认值: {}, 错误: {}", 
                    value, defaultValue, e.getMessage());
            return defaultValue;
        }
    }

    /**
     * 将字符串转换为长整数
     * 如果转换失败或发生异常，返回默认值
     * 适用于时间戳（毫秒数）等需要 Long 类型的场景
     * 
     * @param value 要转换的字符串（可以为 null 或空字符串）
     * @param defaultValue 转换失败时返回的默认值
     * @return 转换后的长整数值，如果转换失败则返回默认值
     */
    public static long parseLong(String value, long defaultValue) {
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            log.warn("字符串转换为长整数失败，使用默认值 - 输入值: \"{}\", 默认值: {}, 错误: {}", 
                    value, defaultValue, e.getMessage());
            return defaultValue;
        }
    }

    /**
     * 将字符串转义后用作 Redis key 的片段（避免与 key 中的冒号等分隔符混淆）。
     * 转义规则：\ → \\，: → \:
     *
     * @param segment 原始片段（如 fromId、conversationId），可为 null 或空
     * @return 转义后的字符串；null/空 返回原值
     */
    public static String escapeForRedisKeySegment(String segment) {
        if (segment == null || segment.isEmpty()) {
            return segment;
        }
        return segment.replace("\\", "\\\\").replace(":", "\\:");
    }

    /**
     * 将 {@link #escapeForRedisKeySegment} 转义过的字符串还原。
     *
     * @param escaped 转义后的片段，可为 null 或空
     * @return 还原后的字符串；null/空 返回原值
     */
    public static String unescapeForRedisKeySegment(String escaped) {
        if (escaped == null || escaped.isEmpty()) {
            return escaped;
        }
        StringBuilder sb = new StringBuilder(escaped.length());
        for (int i = 0; i < escaped.length(); i++) {
            char c = escaped.charAt(i);
            if (c == '\\' && i + 1 < escaped.length()) {
                char next = escaped.charAt(i + 1);
                if (next == '\\' || next == ':') {
                    sb.append(next);
                    i++;
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
