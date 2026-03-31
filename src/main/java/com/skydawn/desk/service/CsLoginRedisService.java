package com.skydawn.desk.service;

import com.skydawn.desk.sockets.CsMessageSendSockets;
import com.skydawn.redis.CsRedisKeys;
import com.skydawn.redis.RedisFinder;
import com.skydawn.redis.RedisOperation;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;

/**
 * 登录/登出时写 Redis：users、user-offline-since；登录后同步负载 ZSET 并从待分配队列拉取会话。
 * user-conversation 仅在分配/转移/结束会话时变更，登出时不清理，以便再登录能拉回原会话。
 * 客服在 Redis 中由登录名（userName）标识，非雪花 userId。
 */
@Service
public class CsLoginRedisService {

    private static final DateTimeFormatter LOGIN_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final RedisOperation redisOperation;
    private final RedisFinder redisFinder;
    private final CsAllocationService csAllocationService;
    private final CsMessageSendSockets sockets;

    public CsLoginRedisService(RedisOperation redisOperation, RedisFinder redisFinder,
                               CsAllocationService csAllocationService, CsMessageSendSockets sockets) {
        this.redisOperation = redisOperation;
        this.redisFinder = redisFinder;
        this.csAllocationService = csAllocationService;
        this.sockets = sockets;
    }

    /** 登录成功（HTTP）：写 users，删 user-offline-since；以 user-conversation 为准修复碎片，再从待分配队列拉取会话。 */
    public void onLoginSuccess(String loginName) {
        String logintime = LocalDateTime.now().format(LOGIN_TIME);
        redisOperation.setUsers(loginName, logintime);
        redisOperation.deleteUserOfflineSince(loginName);
        repairUserConversationFragments(loginName);
        csAllocationService.pullPendingConversationsForAgent(loginName);
    }

    /**
     * 以 user-conversation 为准重建 conversation-user，并清理 pending 中的已分配会话。
     * 谁登录就清理谁的碎片，只处理当前用户。
     */
    private void repairUserConversationFragments(String userId) {
        Set<String> userConvIds = redisFinder.getUserConversationList(userId);
        Set<String> convUserIds = redisFinder.getConversationIdsOwnedByUser(userId);
        for (String convId : convUserIds) {
            if (!userConvIds.contains(convId)) {
                redisOperation.deleteConversationUser(convId);
            }
        }
        for (String convId : userConvIds) {
            redisOperation.setConversationUser(convId, userId);
            redisOperation.pendingConversationsRemove(convId);
        }
    }

    /**
     * 主动登出（HTTP /logout）：
     * 1. 更新 Redis 离线状态。
     * 2. 关闭本实例上该用户的 WebSocket 连接，避免"Redis 已离线但 WS 仍活"的窗口期导致误重分配。
     * 3. 通过 Redis Pub/Sub 通知其它实例也关闭该用户的连接（空 exceptSessionId 匹配所有实例）。
     * 不清理 user-conversation，以便再登录时能拉回原会话。
     */
    public void onLogout(String loginName) {
        redisOperation.deleteUsers(loginName);
        String offlineSince = LocalDateTime.now().format(LOGIN_TIME);
        redisOperation.setUserOfflineSince(loginName, offlineSince);

        // 关闭本实例 WebSocket
        sockets.closeForLogout(loginName);
        // 通知其它实例关闭（空 exceptSessionId 不会排除任何 session）
        redisOperation.publish(CsRedisKeys.CHANNEL_WS_CLOSE_ELSEWHERE, loginName + ":");
    }
}
