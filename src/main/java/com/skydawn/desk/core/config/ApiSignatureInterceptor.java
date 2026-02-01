package com.skydawn.desk.core.config;

import com.skydawn.common.Defs;
import com.skydawn.common.security.ApiSignature;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * API 签名验证拦截器
 */
@Component
public class ApiSignatureInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ApiSignatureInterceptor.class);
    private static final String NONCE_KEY_PREFIX = "api:nonce:";

    @Value("${sys.api.app-id}")
    private String expectedAppId;

    @Value("${sys.api.secret-key}")
    private String secretKey;

    @Value("${sys.api.timestamp-tolerance}")
    private long timestampTolerance;

    private final StringRedisTemplate redisTemplate;

    public ApiSignatureInterceptor(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler)
            throws Exception {

        // 获取签名相关 Headers
        String appId = request.getHeader(Defs.HTTP_HEAD_APP_ID);
        String timestamp = request.getHeader(Defs.HTTP_HEAD_TIMESTAMP);
        String nonce = request.getHeader(Defs.HTTP_HEAD_NONCE);
        String signature = request.getHeader(Defs.HTTP_HEAD_SIGNATURE);

        // 1. 检查必要参数
        if (appId == null || timestamp == null || nonce == null || signature == null) {
            log.warn("签名验证失败 - 缺少必要参数, appId={}, timestamp={}, nonce={}, signature={}",
                    appId, timestamp, nonce, signature != null ? "***" : null);
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Missing signature headers");
            return false;
        }

        // 2. 验证 AppId
        if (!expectedAppId.equals(appId)) {
            log.warn("签名验证失败 - AppId 不匹配: {}", appId);
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid app id");
            return false;
        }

        // 3. 验证时间戳
        long requestTime;
        try {
            requestTime = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            log.warn("签名验证失败 - 时间戳格式错误: {}", timestamp);
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid timestamp");
            return false;
        }

        long currentTime = System.currentTimeMillis();
        if (Math.abs(currentTime - requestTime) > timestampTolerance) {
            log.warn("签名验证失败 - 时间戳过期, requestTime={}, currentTime={}, diff={}ms",
                    requestTime, currentTime, Math.abs(currentTime - requestTime));
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Timestamp expired");
            return false;
        }

        // 4. 验证 Nonce（防重放）
        try {
            String nonceKey = NONCE_KEY_PREFIX + nonce;
            Boolean isNew = redisTemplate.opsForValue().setIfAbsent(nonceKey, "1",
                    timestampTolerance, TimeUnit.MILLISECONDS);
            if (isNew == null || !isNew) {
                log.warn("签名验证失败 - Nonce 重复: {}", nonce);
                sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Duplicate request");
                return false;
            }
        } catch (Exception e) {
            log.error("Redis 连接异常，无法验证 Nonce: {}", e.getMessage());
            sendError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Service temporarily unavailable");
            return false;
        }

        // 5. 验证签名
        String body = getRequestBody(request);
        boolean valid = ApiSignature.verifySignature(appId, timestamp, nonce, body, secretKey, signature);

        if (!valid) {
            log.warn("签名验证失败 - 签名不匹配, appId={}, timestamp={}, nonce={}", appId, timestamp, nonce);
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid signature");
            return false;
        }

        log.debug("签名验证通过 - appId={}, nonce={}", appId, nonce);
        return true;
    }

    /**
     * 获取请求体
     */
    private String getRequestBody(HttpServletRequest request) throws IOException {
        if (request instanceof ContentCachingRequestWrapper wrapper) {
            byte[] content = wrapper.getContentAsByteArray();
            if (content.length > 0) {
                return new String(content, StandardCharsets.UTF_8);
            }
        }
        // 对于 form 表单提交，从参数中构建 body
        if (request.getContentType() != null &&
                request.getContentType().contains("application/x-www-form-urlencoded")) {
            return buildFormBody(request);
        }
        return "";
    }

    /**
     * 构建表单请求体（按参数名排序）
     */
    private String buildFormBody(HttpServletRequest request) {
        StringBuilder sb = new StringBuilder();
        request.getParameterMap().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .forEach(entry -> {
                    for (String value : entry.getValue()) {
                        if (sb.length() > 0) {
                            sb.append("&");
                        }
                        sb.append(entry.getKey()).append("=").append(value);
                    }
                });
        return sb.toString();
    }

    /**
     * 发送错误响应
     */
    private void sendError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
