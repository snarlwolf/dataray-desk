package com.skydawn.desk.core.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.skydawn.desk.core.entity.Message;

@Mapper
public interface MessageMapper {

    Message findById(Long id);

    List<Message> findByConversationId(Long conversationId);

    List<Message> findByRedisConversationId(String redisConversationId);

    /**
     * 是否存在同会话、同 source_message_id、同类型且同状态的记录（用于 STATUS 去重，避免同一回调重复入库）。
     */
    int countByRedisConvIdAndSourceMessageIdAndTypeAndStatus(String redisConversationId, String sourceMessageId, String messageType, String messageStatus);

    int insert(Message message);

    int update(Message message);

    int deleteById(Long id);
}
