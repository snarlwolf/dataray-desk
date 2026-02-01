package com.skydawn.desk.sockets;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.lang.NonNull;

import com.skydawn.redis.CsRedisKeys;

/**
 * 客服台 WebSocket 多实例挤线：订阅 Redis 频道，收到“同账号在其他实例新建 WebSocket”时关闭本实例旧连接。
 */
@Configuration
public class CsWsRedisListenerConfig {

    @Bean
    public RedisMessageListenerContainer csWsCloseElsewhereContainer(
            @NonNull RedisConnectionFactory connectionFactory,
            @NonNull CsWsCloseElsewhereListener closeElsewhereListener) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(closeElsewhereListener, new ChannelTopic(CsRedisKeys.CHANNEL_WS_CLOSE_ELSEWHERE));
        return container;
    }
}
