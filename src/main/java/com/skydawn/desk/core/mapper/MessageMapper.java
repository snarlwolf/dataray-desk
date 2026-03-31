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
     * 按 clientId + officialAccount 查最近一条 message_status='NORMAL' 的消息（用于恢复会话时定位上次会话）。
     */
    Message findLatestNormalByClientIdAndAccount(@org.apache.ibatis.annotations.Param("clientId") String clientId,
                                                  @org.apache.ibatis.annotations.Param("officialAccount") String officialAccount);

    /**
     * 按 conversationId 查最近 limit 条消息，按 send_time 升序返回（用于恢复历史消息到 Redis）。
     */
    List<Message> findRecentByConversationId(@org.apache.ibatis.annotations.Param("conversationId") Long conversationId,
                                              @org.apache.ibatis.annotations.Param("limit") int limit);

    /**
     * 是否存在同会话、同 source_message_id、同类型且同状态的记录（用于 STATUS 去重，避免同一回调重复入库）。
     */
    int countByRedisConvIdAndSourceMessageIdAndTypeAndStatus(String redisConversationId, String sourceMessageId, String messageType, String messageStatus);

    int insert(Message message);

    int update(Message message);

    int deleteById(Long id);
}
