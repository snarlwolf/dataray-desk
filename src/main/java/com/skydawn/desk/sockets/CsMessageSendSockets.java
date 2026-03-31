package com.skydawn.desk.sockets;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.skydawn.redis.CsRedisKeys;
import com.skydawn.redis.RedisOperation;

/**
 * 客服台 WebSocket 会话注册表，按客服登录名（userName）推送消息。
 * 同账号多端登录时，仅保留最新连接；通过 Redis 广播通知其他实例关闭旧连接（多实例部署时）。
 */
@Component
public class CsMessageSendSockets {

    private static final Logger log = LoggerFactory.getLogger(CsMessageSendSockets.class);

    /** 自定义关闭码：同账号在其他地点登录，挤掉当前连接 */
    public static final int CLOSE_LOGGED_IN_ELSEWHERE = 4000;

    private final Map<String, WebSocketSession> userIdToSession = new ConcurrentHashMap<>();
    /** 按 userId 串行化发送，避免同一会话并发 sendMessage 导致 TEXT_PARTIAL_WRITING 异常 */
    private final Map<String, Object> sendLocks = new ConcurrentHashMap<>();
    private final RedisOperation redisOperation;

    public CsMessageSendSockets(RedisOperation redisOperation) {
        this.redisOperation = redisOperation;
    }

    public void register(String userId, WebSocketSession session) {
        if (userId == null || session == null) return;
        String sessionId = session.getId();
        WebSocketSession old = userIdToSession.put(Objects.requireNonNull(userId), Objects.requireNonNull(session));
        if (old != null && old.isOpen()) {
            try {
                old.close(new CloseStatus(CLOSE_LOGGED_IN_ELSEWHERE, "Logged in elsewhere"));
            } catch (IOException e) {
                log.debug("close old session", e);
            }
        }
        redisOperation.publish(CsRedisKeys.CHANNEL_WS_CLOSE_ELSEWHERE, userId + ":" + sessionId);
        log.info("WebSocket registered for userId={}", userId);
    }

    /**
     * 收到 Redis 广播：同账号在其他实例新建了 WebSocket，本实例若持有该 userId 的旧连接则关闭（不发 4000 时由 afterConnectionClosed 负责 unregister）。
     */
    public void closeUserSessionElsewhere(String userId, String exceptSessionId) {
        if (userId == null) return;
        WebSocketSession current = userIdToSession.get(Objects.requireNonNull(userId));
        if (current == null || !current.isOpen()) return;
        if (Objects.equals(current.getId(), exceptSessionId)) return;
        try {
            current.close(new CloseStatus(CLOSE_LOGGED_IN_ELSEWHERE, "Logged in elsewhere"));
            log.info("WebSocket closed for userId={} (new session on other instance)", userId);
        } catch (IOException e) {
            log.debug("close session elsewhere", e);
        }
    }

    /**
     * 仅当当前关闭的 session 仍是 map 中该 userId 的会话时才移除，避免“新登录挤掉旧连接”时
     * 旧连接的 afterConnectionClosed 误删新连接的注册，导致双方都收不到消息。
     */
    public void unregister(String userId, WebSocketSession closedSession) {
        if (userId == null) return;
        WebSocketSession current = userIdToSession.get(Objects.requireNonNull(userId));
        if (current == closedSession) {
            userIdToSession.remove(userId);
            sendLocks.remove(userId);
            log.info("WebSocket unregistered for userId={}", userId);
        }
    }

    /**
     * 向指定用户推送文本消息；若该用户未连接则返回 false。
     * 按 userId 串行发送，避免同一会话并发 sendMessage 触发 IllegalStateException(TEXT_PARTIAL_WRITING)。
     */
    public boolean sendToUser(String userId, String text) {
        if (userId == null || text == null) return false;
        WebSocketSession session = userIdToSession.get(Objects.requireNonNull(userId));
        if (session == null || !session.isOpen()) return false;
        Object lock = sendLocks.computeIfAbsent(userId, k -> new Object());
        synchronized (lock) {
            if (!session.isOpen()) return false;
            try {
                session.sendMessage(new TextMessage(Objects.requireNonNull(text)));
                return true;
            } catch (IOException e) {
                log.warn("sendToUser failed userId={}", userId, e);
                return false;
            } catch (IllegalStateException e) {
                log.warn("sendToUser endpoint busy (concurrent send?) userId={}", userId, e);
                return false;
            }
        }
    }

    /**
     * 主动登出：关闭本实例上该用户的 WebSocket 连接（正常关闭，1000）。
     * 同时由调用方通过 Redis Pub/Sub 通知其它实例做同样处理。
     */
    public void closeForLogout(String userId) {
        if (userId == null) return;
        WebSocketSession session = userIdToSession.get(Objects.requireNonNull(userId));
        if (session == null || !session.isOpen()) return;
        try {
            session.close(new CloseStatus(CloseStatus.NORMAL.getCode()));
            log.info("WebSocket closed for logout userId={}", userId);
        } catch (IOException e) {
            log.debug("closeForLogout failed userId={}", userId, e);
        }
    }

    public boolean isOnline(String userId) {
        if (userId == null) return false;
        WebSocketSession session = userIdToSession.get(Objects.requireNonNull(userId));
        return session != null && session.isOpen();
    }

    /** 返回本实例当前所有已连接的用户名快照（用于推送空列表、清理冗余数据等场景） */
    public Set<String> getLocalUserNames() {
        return Set.copyOf(userIdToSession.keySet());
    }
}
