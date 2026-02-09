package com.skydawn.desk.service;

import com.skydawn.redis.RedisOperation;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 登录/登出时写 Redis：users、user-offline-since；登录后同步负载 ZSET 并从待分配队列拉取会话。
 * user-conversation 仅在分配/转移/结束会话时变更，登出时不清理，以便再登录能拉回原会话。
 * 客服在 Redis 中由登录名（userName）标识，非雪花 userId。
 */
@Service
public class CsLoginRedisService {

    private static final DateTimeFormatter LOGIN_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final RedisOperation redisOperation;
    private final CsAllocationService csAllocationService;

    public CsLoginRedisService(RedisOperation redisOperation, CsAllocationService csAllocationService) {
        this.redisOperation = redisOperation;
        this.csAllocationService = csAllocationService;
    }

    /** 登录成功（HTTP）：写 users，删 user-offline-since；初始化负载并从待分配队列拉取最多 min(5, 剩余容量) 条会话。 */
    public void onLoginSuccess(String loginName) {
        String logintime = LocalDateTime.now().format(LOGIN_TIME);
        redisOperation.setUsers(loginName, logintime);
        redisOperation.deleteUserOfflineSince(loginName);
        csAllocationService.pullPendingConversationsForAgent(loginName);
    }

    /** 主动登出（HTTP /logout）：删 users，写 user-offline-since（TTL）。不清理 user-conversation，以便再登录时能拉回原会话；登录状态由 users / user-offline-since 判断。 */
    public void onLogout(String loginName) {
        redisOperation.deleteUsers(loginName);
        String offlineSince = LocalDateTime.now().format(LOGIN_TIME);
        redisOperation.setUserOfflineSince(loginName, offlineSince);
    }
}
