package com.skydawn.desk.message.waba;

import com.skydawn.desk.dto.GeneralMessageDto;
import com.skydawn.ingest.dto.WabaMessageDto;

/**
 * WabaMessageDto 转 GeneralMessageDto：messageSource="waba"，clientId 来自 fromWaId 或状态消息的 recipientId。
 */
public final class WabaToGeneralMessageConverter {

    private WabaToGeneralMessageConverter() {}

    public static GeneralMessageDto fromWaba(WabaMessageDto w) {
        GeneralMessageDto g = new GeneralMessageDto();
        g.setMessageSource("waba");
        // 状态消息（SENT/DELIVERED/READ）用渠道侧 recipientId 作为会话归属，便于按会话路由
        if (w.getMessageType() == WabaMessageDto.MessageType.STATUS && w.getRecipientId() != null) {
            g.setClientId(w.getRecipientId());
        } else {
            g.setClientId(w.getFromWaId());
        }
        g.setAccountId(w.getAccountId());
        g.setOfficialPhoneNumber(w.getDisplayPhoneNumber());
        g.setOfficialAccount(w.getPhoneNumberId());
        g.setSourceMessageId(w.getMessageId());
        g.setMessageType(mapType(w.getMessageType()));
        g.setMessageStatus(mapStatus(w.getMessageStatus()));
        g.setTimestamp(w.getTimestamp());
        g.setClientName(w.getFromProfileName());
        g.setTextBody(w.getTextBody());
        g.setMediaId(w.getMediaId());
        g.setMediaUrl(w.getMediaUrl());
        g.setMediaMimeType(w.getMediaMimeType());
        g.setMediaSha256(w.getMediaSha256());
        g.setMediaCaption(w.getMediaCaption());
        String refId = w.getReactionMessageId() != null && !w.getReactionMessageId().isBlank()
                ? w.getReactionMessageId() : w.getQuotedMessageId();
        if (refId != null) g.setReferencedMessageId(refId);
        g.setReactionEmoji(w.getReactionEmoji());
        g.setLatitude(w.getLatitude());
        g.setLongitude(w.getLongitude());
        return g;
    }

    private static GeneralMessageDto.MessageType mapType(WabaMessageDto.MessageType t) {
        if (t == null) return GeneralMessageDto.MessageType.UNKNOWN;
        return switch (t) {
            case TEXT -> GeneralMessageDto.MessageType.TEXT;
            case IMAGE -> GeneralMessageDto.MessageType.IMAGE;
            case STICKER -> GeneralMessageDto.MessageType.STICKER;
            case VIDEO -> GeneralMessageDto.MessageType.VIDEO;
            case AUDIO -> GeneralMessageDto.MessageType.AUDIO;
            case DOCUMENT -> GeneralMessageDto.MessageType.DOCUMENT;
            case LOCATION -> GeneralMessageDto.MessageType.LOCATION;
            case CONTACTS -> GeneralMessageDto.MessageType.CONTACTS;
            case STATUS -> GeneralMessageDto.MessageType.STATUS;
            case REACTION -> GeneralMessageDto.MessageType.REACTION;
            case UNSUPPORTED -> GeneralMessageDto.MessageType.UNSUPPORTED;
            default -> GeneralMessageDto.MessageType.UNKNOWN;
        };
    }

    private static GeneralMessageDto.MessageStatus mapStatus(WabaMessageDto.MessageStatus s) {
        if (s == null) return GeneralMessageDto.MessageStatus.UNKNOWN;
        return switch (s) {
            case NORMAL -> GeneralMessageDto.MessageStatus.NORMAL;
            case SENT -> GeneralMessageDto.MessageStatus.SENT;
            case DELIVERED -> GeneralMessageDto.MessageStatus.DELIVERED;
            case READ -> GeneralMessageDto.MessageStatus.READ;
            case UNSUPPORTED -> GeneralMessageDto.MessageStatus.UNSUPPORTED;
            default -> GeneralMessageDto.MessageStatus.UNKNOWN;
        };
    }
}
