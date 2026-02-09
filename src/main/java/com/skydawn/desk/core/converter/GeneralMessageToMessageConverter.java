package com.skydawn.desk.core.converter;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import com.skydawn.common.utils.IdCreator;
import com.skydawn.desk.core.entity.Message;
import com.skydawn.desk.dto.GeneralMessageDto;

/**
 * 将流转 DTO {@link GeneralMessageDto} 转为持久化实体 {@link Message}。
 * <p>
 * 注意：转换类（DTO）的 conversationId 对应的是实体类的 {@code redis_conversation_id}，不是实体类的 conversationId。
 * 实体类的 conversation_id 为库表 conversation 主键，由方法参数传入。
 * </p>
 * 仅用于「对方（客户）消息」的落库转换时，is_staff=0、sys_user_id=null。
 */
public final class GeneralMessageToMessageConverter {

    private GeneralMessageToMessageConverter() {}

    /**
     * 将 GeneralMessageDto 转为 Message 实体（用于对方消息落库）。
     *
     * @param dto              流转消息 DTO，不可为 null
     * @param conversationId  会话主键（库表 conversation.id），对应实体的 conversation_id 字段
     * @param messageLanguage 消息语言，可为 null
     * @return 未持久化的 Message 实体，id 已用雪花 ID 填充；redis_conversation_id 来自 dto.conversationId（与实体的 conversationId 不同）
     */
    public static Message toMessage(GeneralMessageDto dto, Long conversationId, String messageLanguage) {
        Message msg = new Message();
        msg.setId(IdCreator.createSnowId());
        msg.setConversationId(conversationId);                                    // 实体 conversation_id：参数传入（库表主键）
        msg.setMessageSource(dto.getMessageSource() != null ? dto.getMessageSource() : "unknown");
        msg.setOfficialPhoneNumber(defaultIfBlank(dto.getOfficialPhoneNumber(), "unknown"));
        msg.setOfficialAccount(defaultIfBlank(dto.getOfficialAccount(), "unknown"));
        msg.setSourceMessageId(defaultIfBlank(dto.getSourceMessageId(), "unknown"));
        msg.setMessageType(dto.getMessageType() != null ? dto.getMessageType().name() : "UNKNOWN");
        msg.setMessageStatus(dto.getMessageStatus() != null ? dto.getMessageStatus().name() : "NORMAL");
        msg.setSendTime(toOffsetDateTime(dto.getTimestamp()));
        msg.setRedisConversationId(defaultIfBlank(dto.getConversationId(), "unknown")); // 实体 redis_conversation_id：来自 DTO.conversationId
        msg.setClientId(dto.getClientId()); // 外部系统客户标识，存字符串
        msg.setClientName(dto.getClientName());
        msg.setMessageLanguage(messageLanguage);
        msg.setTextBody(dto.getTextBody());
        msg.setMediaId(dto.getMediaId());
        msg.setMediaUrl(dto.getMediaUrl());
        msg.setMediaMimeType(dto.getMediaMimeType());
        msg.setMediaSha256(dto.getMediaSha256());
        msg.setMediaCaption(dto.getMediaCaption());
        msg.setReferencedMessageId(dto.getReferencedMessageId());
        msg.setReactionEmoji(dto.getReactionEmoji());
        msg.setLatitude(dto.getLatitude());
        msg.setLongitude(dto.getLongitude());
        msg.setIsStaff(0);
        msg.setSysUserId(null);
        msg.setDelFlag("0");
        msg.setRemarks(null);
        return msg;
    }

    private static String defaultIfBlank(String s, String defaultVal) {
        return (s != null && !s.isBlank()) ? s : defaultVal;
    }

    private static OffsetDateTime toOffsetDateTime(long epochSeconds) {
        return OffsetDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneId.systemDefault());
    }
}
