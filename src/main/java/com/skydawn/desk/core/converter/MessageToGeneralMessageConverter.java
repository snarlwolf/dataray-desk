package com.skydawn.desk.core.converter;

import com.skydawn.desk.core.entity.Message;
import com.skydawn.desk.dto.GeneralMessageDto;

/**
 * 将持久化实体 {@link Message} 反向转换为流转 DTO {@link GeneralMessageDto}。
 * 用于从数据库恢复历史消息到 Redis 会话时，将 DB 记录还原为前端推送格式。
 */
public final class MessageToGeneralMessageConverter {

    private MessageToGeneralMessageConverter() {}

    /**
     * 将 Message 实体转换为 GeneralMessageDto。
     *
     * @param msg             数据库消息记录，不可为 null
     * @param redisConvId     Redis 会话 id（覆盖 msg.redisConversationId，保证与当前会话一致）
     * @return GeneralMessageDto，可直接序列化后写入 Redis 会话消息列表
     */
    public static GeneralMessageDto fromMessage(Message msg, String redisConvId) {
        GeneralMessageDto dto = new GeneralMessageDto();

        dto.setMessageSource(msg.getMessageSource());
        dto.setOfficialPhoneNumber(msg.getOfficialPhoneNumber());
        dto.setOfficialAccount(msg.getOfficialAccount());
        dto.setSourceMessageId(msg.getSourceMessageId());
        dto.setConversationId(redisConvId != null ? redisConvId : msg.getRedisConversationId());
        dto.setClientId(msg.getClientId());
        dto.setClientName(msg.getClientName());
        dto.setTextBody(msg.getTextBody());
        dto.setMediaId(msg.getMediaId());
        dto.setMediaUrl(msg.getMediaUrl());
        dto.setMediaMimeType(msg.getMediaMimeType());
        dto.setMediaSha256(msg.getMediaSha256());
        dto.setMediaCaption(msg.getMediaCaption());
        dto.setReferencedMessageId(msg.getReferencedMessageId());
        dto.setReactionEmoji(msg.getReactionEmoji());
        dto.setLatitude(msg.getLatitude());
        dto.setLongitude(msg.getLongitude());
        dto.setIsStaff(msg.getIsStaff() != null && msg.getIsStaff() == 1);

        // 系统自动回复：sys_user_id 为 AISYSTEM（AI 侧）或 AUTO（本机自动回复/结束会话/不支持提示等）
        if (msg.getSysUserId() != null) {
            String sid = msg.getSysUserId();
            if ("AISYSTEM".equals(sid) || "AUTO".equals(sid)) {
                dto.setIsSystemReply(true);
            } else {
                dto.setCsStaffName(sid);
            }
        }

        if (msg.getMessageType() != null) {
            try {
                dto.setMessageType(GeneralMessageDto.MessageType.valueOf(msg.getMessageType()));
            } catch (IllegalArgumentException e) {
                dto.setMessageType(GeneralMessageDto.MessageType.UNKNOWN);
            }
        }

        if (msg.getMessageStatus() != null) {
            try {
                dto.setMessageStatus(GeneralMessageDto.MessageStatus.valueOf(msg.getMessageStatus()));
            } catch (IllegalArgumentException e) {
                dto.setMessageStatus(GeneralMessageDto.MessageStatus.NORMAL);
            }
        }

        if (msg.getSendTime() != null) {
            dto.setTimestamp(msg.getSendTime().toInstant().getEpochSecond());
        }

        return dto;
    }
}
