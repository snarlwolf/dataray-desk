package com.skydawn.desk.sockets;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.skydawn.desk.core.controller.SysUserLoginController;
import com.skydawn.desk.core.entity.SysUser;
import com.skydawn.redis.RedisOperation;

/**
 * 客服台 WebSocket 处理器：连接时注册并写 Redis，断开时注销并写 offline-since。
 */
@Component
public class CsWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(CsWebSocketHandler.class);
    private static final DateTimeFormatter LOGIN_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final CsMessageSendSockets sockets;
    private final RedisOperation redisOperation;

    public CsWebSocketHandler(CsMessageSendSockets sockets, RedisOperation redisOperation) {
        this.sockets = sockets;
        this.redisOperation = redisOperation;
    }

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) throws Exception {
        Object attr = session.getAttributes().get(SysUserLoginController.SESSION_USER_KEY);
        if (!(attr instanceof SysUser user)) {
            log.warn("WebSocket connect without login user, close");
            session.close(Objects.requireNonNull(CloseStatus.POLICY_VIOLATION));
            return;
        }
        String loginName = user.getUserName();

        // 删除曾下线的记录（若存在）
        redisOperation.deleteUserOfflineSince(loginName);

        // users：在线标记；user-conversation 不在此处写入，重连时保留原列表（仅分配会话时 SADD）
        String logintime = LocalDateTime.now().format(LOGIN_TIME);
        redisOperation.setUsers(loginName, logintime);

        sockets.register(loginName, session);
    }

    @Override
    protected void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage message) throws Exception {
        String payload = message.getPayload();
        if (payload != null && payload.contains("\"kind\"") && payload.contains("\"ping\"")) {
            session.sendMessage(new TextMessage("{\"kind\":\"pong\"}"));
        }
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) throws Exception {
        Object attr = session.getAttributes().get(SysUserLoginController.SESSION_USER_KEY);
        if (!(attr instanceof SysUser user)) {
            return;
        }
        String loginName = user.getUserName();
        sockets.unregister(loginName, session);
        try {
            redisOperation.deleteUsers(loginName);
            String offlineSince = LocalDateTime.now().format(LOGIN_TIME);
            redisOperation.setUserOfflineSince(loginName, offlineSince);
            log.info("WebSocket closed loginName={}, offlineSince={}", loginName, offlineSince);
        } catch (IllegalStateException e) {
            // 应用关闭时 Redis 已销毁，忽略
            if (e.getMessage() == null || !e.getMessage().contains("destroyed")) {
                throw e;
            }
            log.debug("afterConnectionClosed skip Redis (shutting down) loginName={}", loginName);
        }
    }
}
