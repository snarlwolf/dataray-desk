package com.skydawn.desk.sockets;

import java.nio.charset.StandardCharsets;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import com.skydawn.redis.CsRedisKeys;

/**
 * 订阅 Redis 频道：同账号在其他实例新建 WebSocket 时，本实例关闭该客服（登录名）的旧连接。
 */
@Component
public class CsWsCloseElsewhereListener implements MessageListener {

    private final CsMessageSendSockets sockets;

    public CsWsCloseElsewhereListener(CsMessageSendSockets sockets) {
        this.sockets = sockets;
    }

    @Override
    public void onMessage(@NonNull Message message, @Nullable byte[] pattern) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        int colon = body.indexOf(':');
        if (colon <= 0) return;
        String userId = body.substring(0, colon);
        String exceptSessionId = (colon + 1 < body.length()) ? body.substring(colon + 1) : "";
        sockets.closeUserSessionElsewhere(userId, exceptSessionId);
    }

    public static String channel() {
        return CsRedisKeys.CHANNEL_WS_CLOSE_ELSEWHERE;
    }
}
