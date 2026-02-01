package com.skydawn.desk.service;

import com.skydawn.redis.RedisOperation;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 登录/登出时写 Redis：users、user-offline-since。user-conversation 仅在分配/转移/结束会话时变更，登出时不清理，以便再登录能拉回原会话。
 * user-offline-since 带 24h TTL，自动过期，不造成 Redis 堆积；若需长期审计，可在此处或定时任务中将登录/下线事件写入数据库日志。
 * 客服在 Redis 中由登录名（userName）标识，非雪花 userId。
 */
@Service
public class CsLoginRedisService {

    private static final DateTimeFormatter LOGIN_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final RedisOperation redisOperation;

    public CsLoginRedisService(RedisOperation redisOperation) {
        this.redisOperation = redisOperation;
    }

    /** 登录成功（HTTP）：写 users，删 user-offline-since。loginName 为客服登录名。 */
    public void onLoginSuccess(String loginName) {
        String logintime = LocalDateTime.now().format(LOGIN_TIME);
        redisOperation.setUsers(loginName, logintime);
        redisOperation.deleteUserOfflineSince(loginName);
    }

    /** 主动登出（HTTP /logout）：删 users，写 user-offline-since（TTL）。不清理 user-conversation，以便再登录时能拉回原会话；登录状态由 users / user-offline-since 判断。 */
    public void onLogout(String loginName) {
        redisOperation.deleteUsers(loginName);
        String offlineSince = LocalDateTime.now().format(LOGIN_TIME);
        redisOperation.setUserOfflineSince(loginName, offlineSince);
    }
}
