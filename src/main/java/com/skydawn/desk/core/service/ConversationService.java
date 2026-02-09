package com.skydawn.desk.core.service;

import org.springframework.stereotype.Service;

import com.skydawn.common.utils.IdCreator;
import com.skydawn.desk.core.entity.Conversation;
import com.skydawn.desk.core.mapper.ConversationMapper;
import com.skydawn.redis.RedisOperation;

/**
 * 会话库表：按 Redis 会话 id 解析 conversation_id。redis_conversation_id 仅存 message 表供流转用，不入 conversation 表；
 * Redis 会话 id -> conversation.id 的映射仅缓存在 Redis。
 */
@Service
public class ConversationService {

    private final ConversationMapper conversationMapper;
    private final RedisOperation redisOperation;

    public ConversationService(ConversationMapper conversationMapper, RedisOperation redisOperation) {
        this.conversationMapper = conversationMapper;
        this.redisOperation = redisOperation;
    }

    /**
     * 按 Redis 会话 id 获取或创建库表会话，返回 conversation.id。先查 Redis 缓存，无则建新会话并写入缓存。
     *
     * @param redisConversationId Redis 会话 id（如 officialAccount-clientId）
     * @param accountNo          账号/推广号，可为 null
     * @param channel            渠道（如 "waba"），可为 null
     * @return conversation.id，不会为 null
     */
    public Long getOrCreateByRedisConversationId(String redisConversationId, String accountNo, String channel) {
        if (redisConversationId == null || redisConversationId.isBlank()) {
            redisConversationId = "unknown";
        }
        Long cached = redisOperation.getConversationDbId(redisConversationId);
        if (cached != null) {
            return cached;
        }
        Conversation conv = new Conversation();
        conv.setId(IdCreator.createSnowId());
        conv.setAccountNo(accountNo);
        conv.setChannel(channel);
        conversationMapper.insert(conv);
        redisOperation.setConversationDbId(redisConversationId, conv.getId());
        return conv.getId();
    }
}
