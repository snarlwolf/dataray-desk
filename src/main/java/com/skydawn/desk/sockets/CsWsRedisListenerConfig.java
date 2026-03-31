package com.skydawn.desk.sockets;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.lang.NonNull;

import com.skydawn.redis.CsRedisKeys;

/**
 * 客服台 WebSocket Redis Pub/Sub 订阅配置：
 * <ul>
 *   <li>挤线频道：同账号在其他实例新建 WebSocket 时关闭本实例旧连接。</li>
 *   <li>同事通知频道：某部门有客服上/下线时，各实例向本地该部门在线用户推送全量同事列表。</li>
 * </ul>
 */
@Configuration
public class CsWsRedisListenerConfig {

    @Bean
    public RedisMessageListenerContainer csWsRedisListenerContainer(
            @NonNull RedisConnectionFactory connectionFactory,
            @NonNull CsWsCloseElsewhereListener closeElsewhereListener,
            @NonNull CsColleaguesNotifyListener colleaguesNotifyListener) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(closeElsewhereListener,
                new ChannelTopic(CsRedisKeys.CHANNEL_WS_CLOSE_ELSEWHERE));
        container.addMessageListener(colleaguesNotifyListener,
                new ChannelTopic(CsRedisKeys.CHANNEL_COLLEAGUES_NOTIFY));
        return container;
    }
}
