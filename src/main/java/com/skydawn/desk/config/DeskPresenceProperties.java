package com.skydawn.desk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 客服 Redis 在线续期 / {@code users:*} TTL（见 docs/redis-online-heartbeat-design.md）。
 */
@ConfigurationProperties(prefix = "sys.desk.presence")
public class DeskPresenceProperties {

    /**
     * {@code desk:cs:users:*} 过期秒数；应大于约 2× WebSocket ping 间隔。
     */
    private int usersTtlSeconds = 120;

    /**
     * {@code GET /desk/currentUser} 两次续期间最小间隔（毫秒），避免高频轮询刷 Redis。
     */
    private long httpRenewMinIntervalMs = 30_000L;

    public int getUsersTtlSeconds() {
        return usersTtlSeconds;
    }

    public void setUsersTtlSeconds(int usersTtlSeconds) {
        this.usersTtlSeconds = usersTtlSeconds;
    }

    public long getHttpRenewMinIntervalMs() {
        return httpRenewMinIntervalMs;
    }

    public void setHttpRenewMinIntervalMs(long httpRenewMinIntervalMs) {
        this.httpRenewMinIntervalMs = httpRenewMinIntervalMs;
    }
}
