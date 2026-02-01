package com.skydawn.ingest.parser;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.skydawn.ingest.dto.WabaMessageDto;
import com.skydawn.ingest.dto.WabaMessageDto.MessageStatus;
import com.skydawn.ingest.dto.WabaMessageDto.MessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * WhatsApp Business API 消息解析器
 */
public final class WabaParser {

    private static final Logger log = LoggerFactory.getLogger(WabaParser.class);

    private WabaParser() {
        // 工具类，禁止实例化
    }

    /**
     * 解析 WABA 消息
     *
     * @param sourceMessage 原始 JSON 消息
     * @return 解析后的 DTO，解析失败返回 null
     */
    public static WabaMessageDto parseWabaMessage(String sourceMessage) {
        if (sourceMessage == null || sourceMessage.isBlank()) {
            log.warn("收到空消息");
            return null;
        }

        try {
            JsonObject root = JsonParser.parseString(sourceMessage).getAsJsonObject();

            // 检查是否是 WhatsApp Business Account 消息
            String objectType = getAsString(root, "object");
            if (!"whatsapp_business_account".equals(objectType)) {
                log.warn("非 WhatsApp Business Account 消息: {}", objectType);
                return null;
            }

            // 获取 entry 数组
            JsonArray entryArray = root.getAsJsonArray("entry");
            if (entryArray == null || entryArray.isEmpty()) {
                log.warn("entry 数组为空");
                return null;
            }

            // 取第一个 entry
            JsonObject entry = entryArray.get(0).getAsJsonObject();
            String accountId = getAsString(entry, "id");

            // 获取 changes 数组
            JsonArray changesArray = entry.getAsJsonArray("changes");
            if (changesArray == null || changesArray.isEmpty()) {
                log.warn("changes 数组为空");
                return null;
            }

            // 取第一个 change
            JsonObject change = changesArray.get(0).getAsJsonObject();
            JsonObject value = change.getAsJsonObject("value");
            if (value == null) {
                log.warn("value 对象为空");
                return null;
            }

            // 创建 DTO
            WabaMessageDto dto = new WabaMessageDto();
            dto.setAccountId(accountId);
            dto.setRawJson(sourceMessage);

            // 解析 metadata
            parseMetadata(value, dto);

            // 判断是普通消息还是状态消息
            if (value.has("messages")) {
                // 普通消息（文本、图片等）
                parseMessage(value, dto);
            } else if (value.has("statuses")) {
                // 状态消息（sent/delivered/read）
                parseStatus(value, dto);
            } else {
                log.warn("未知消息类型，既没有 messages 也没有 statuses");
                return null;
            }

            return dto;

        } catch (Exception e) {
            log.error("解析 WABA 消息失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 解析 metadata
     */
    private static void parseMetadata(JsonObject value, WabaMessageDto dto) {
        JsonObject metadata = value.getAsJsonObject("metadata");
        if (metadata != null) {
            dto.setDisplayPhoneNumber(getAsString(metadata, "display_phone_number"));
            dto.setPhoneNumberId(getAsString(metadata, "phone_number_id"));
        }
    }

    /**
     * 解析普通消息（文本、图片等）
     */
    private static void parseMessage(JsonObject value, WabaMessageDto dto) {
        // 解析联系人信息
        JsonArray contacts = value.getAsJsonArray("contacts");
        if (contacts != null && !contacts.isEmpty()) {
            JsonObject contact = contacts.get(0).getAsJsonObject();
            dto.setFromWaId(getAsString(contact, "wa_id"));

            JsonObject profile = contact.getAsJsonObject("profile");
            if (profile != null) {
                dto.setFromProfileName(getAsString(profile, "name"));
            }
        }

        // 解析消息内容
        JsonArray messages = value.getAsJsonArray("messages");
        if (messages == null || messages.isEmpty()) {
            return;
        }

        JsonObject message = messages.get(0).getAsJsonObject();
        dto.setMessageId(getAsString(message, "id"));
        dto.setTimestamp(getAsLong(message, "timestamp"));
        // from 在 message 里一定有；contacts 有时缺失，用 message.from 兜底（from 可能是字符串或数字）
        if (dto.getFromWaId() == null || dto.getFromWaId().isBlank()) {
            dto.setFromWaId(getAsStringOrNumber(message, "from"));
        }

        // 回复某条消息时：context.id 为被回复的消息 ID
        parseContext(message, dto);

        // 正常消息状态
        dto.setMessageStatus(MessageStatus.NORMAL);

        // 解析消息类型
        String type = getAsString(message, "type");
        MessageType messageType = parseMessageType(type);
        dto.setMessageType(messageType);

        // 根据类型解析具体内容
        switch (messageType) {
            case TEXT -> parseTextMessage(message, dto);
            case IMAGE -> parseImageMessage(message, dto);
            case VIDEO -> parseVideoMessage(message, dto);
            case AUDIO -> parseAudioMessage(message, dto);
            case DOCUMENT -> parseDocumentMessage(message, dto);
            case LOCATION -> parseLocationMessage(message, dto);
            case CONTACTS -> parseContactsMessage(message, dto);
            case REACTION -> parseReaction(message, dto);
            case UNSUPPORTED -> parseUnsupportedMessage(dto);
            default -> log.info("未处理的消息类型: {}", type);
        }
    }

    /**
     * 解析回复/引用：context.id 为被回复的消息 ID
     */
    private static void parseContext(JsonObject message, WabaMessageDto dto) {
        JsonObject context = message.getAsJsonObject("context");
        if (context != null) {
            dto.setQuotedMessageId(getAsString(context, "id"));
        }
    }

    /**
     * 解析反应消息（点赞等）：reaction.message_id 为被反应的消息，reaction.emoji 为表情
     */
    private static void parseReaction(JsonObject message, WabaMessageDto dto) {
        JsonObject reaction = message.getAsJsonObject("reaction");
        if (reaction != null) {
            dto.setReactionMessageId(getAsString(reaction, "message_id"));
            dto.setReactionEmoji(getAsString(reaction, "emoji"));
        }
    }

    /**
     * 解析文本消息
     */
    private static void parseTextMessage(JsonObject message, WabaMessageDto dto) {
        JsonObject text = message.getAsJsonObject("text");
        if (text != null) {
            dto.setTextBody(getAsString(text, "body"));
        }
    }

    /**
     * 解析图片消息
     */
    private static void parseImageMessage(JsonObject message, WabaMessageDto dto) {
        JsonObject image = message.getAsJsonObject("image");
        if (image != null) {
            dto.setMediaId(getAsString(image, "id"));
            dto.setMediaUrl(getAsString(image, "url"));
            dto.setMediaMimeType(getAsString(image, "mime_type"));
            dto.setMediaSha256(getAsString(image, "sha256"));
            dto.setMediaCaption(getAsString(image, "caption"));
        }
    }

    /**
     * 解析视频消息
     */
    private static void parseVideoMessage(JsonObject message, WabaMessageDto dto) {
        JsonObject video = message.getAsJsonObject("video");
        if (video != null) {
            dto.setMediaId(getAsString(video, "id"));
            dto.setMediaUrl(getAsString(video, "url"));
            dto.setMediaMimeType(getAsString(video, "mime_type"));
            dto.setMediaSha256(getAsString(video, "sha256"));
            dto.setMediaCaption(getAsString(video, "caption"));
        }
    }

    /**
     * 解析音频消息
     */
    private static void parseAudioMessage(JsonObject message, WabaMessageDto dto) {
        JsonObject audio = message.getAsJsonObject("audio");
        if (audio != null) {
            dto.setMediaId(getAsString(audio, "id"));
            dto.setMediaUrl(getAsString(audio, "url"));
            dto.setMediaMimeType(getAsString(audio, "mime_type"));
            dto.setMediaSha256(getAsString(audio, "sha256"));
        }
    }

    /**
     * 解析文档消息
     */
    private static void parseDocumentMessage(JsonObject message, WabaMessageDto dto) {
        JsonObject document = message.getAsJsonObject("document");
        if (document != null) {
            dto.setMediaId(getAsString(document, "id"));
            dto.setMediaUrl(getAsString(document, "url"));
            dto.setMediaMimeType(getAsString(document, "mime_type"));
            dto.setMediaSha256(getAsString(document, "sha256"));
            dto.setMediaCaption(getAsString(document, "caption"));
        }
    }

    /**
     * 解析位置消息：location.latitude、location.longitude
     */
    private static void parseLocationMessage(JsonObject message, WabaMessageDto dto) {
        JsonObject location = message.getAsJsonObject("location");
        if (location != null) {
            dto.setLatitude(getAsDouble(location, "latitude"));
            dto.setLongitude(getAsDouble(location, "longitude"));
        }
    }

    /**
     * 解析名片消息（type=contacts）：支持多条名片，contacts[].name.formatted_name / first_name，phones[].phone
     */
    private static void parseContactsMessage(JsonObject message, WabaMessageDto dto) {
        JsonArray contactsArray = message.getAsJsonArray("contacts");
        if (contactsArray == null || contactsArray.isEmpty()) {
            dto.setTextBody("[名片]");
            return;
        }
        StringBuilder body = new StringBuilder();
        for (int c = 0; c < contactsArray.size(); c++) {
            if (c > 0) {
                body.append("\n\n");
            }
            JsonObject contact = contactsArray.get(c).getAsJsonObject();
            String name = null;
            JsonObject nameObj = contact.getAsJsonObject("name");
            if (nameObj != null) {
                name = getAsString(nameObj, "formatted_name");
                if (name == null || name.isBlank()) {
                    name = getAsString(nameObj, "first_name");
                }
                if (name != null) {
                    name = name.trim();
                }
            }
            body.append(name != null && !name.isBlank() ? name : "[名片]");
            JsonArray phones = contact.getAsJsonArray("phones");
            if (phones != null && !phones.isEmpty()) {
                for (int i = 0; i < phones.size(); i++) {
                    JsonObject p = phones.get(i).getAsJsonObject();
                    String phone = getAsString(p, "phone");
                    if (phone != null && !phone.isBlank()) {
                        body.append("\n").append(phone);
                    }
                }
            }
        }
        dto.setTextBody(body.toString());
    }

    /**
     * 解析不支持的消息（type=unsupported）：events、poll 等，设置提示文案
     */
    private static void parseUnsupportedMessage(WabaMessageDto dto) {
        dto.setMessageStatus(MessageStatus.UNSUPPORTED);
        dto.setTextBody("🚫客户发送了本平台不支持的消息格式。");
    }

    /**
     * 解析状态消息（sent/delivered/read）
     */
    private static void parseStatus(JsonObject value, WabaMessageDto dto) {
        dto.setMessageType(MessageType.STATUS);

        JsonArray statuses = value.getAsJsonArray("statuses");
        if (statuses == null || statuses.isEmpty()) {
            return;
        }

        JsonObject status = statuses.get(0).getAsJsonObject();
        dto.setMessageId(getAsString(status, "id"));
        dto.setTimestamp(getAsLong(status, "timestamp"));
        dto.setRecipientId(getAsString(status, "recipient_id"));

        // 解析状态
        String statusStr = getAsString(status, "status");
        dto.setMessageStatus(parseMessageStatus(statusStr));

        // 解析计费信息
        JsonObject pricing = status.getAsJsonObject("pricing");
        if (pricing != null) {
            dto.setBillable(getAsBoolean(pricing, "billable"));
            dto.setPricingModel(getAsString(pricing, "pricing_model"));
            dto.setPricingCategory(getAsString(pricing, "category"));
            dto.setPricingType(getAsString(pricing, "type"));
        }
    }

    /**
     * 解析消息类型字符串
     */
    private static MessageType parseMessageType(String type) {
        if (type == null) {
            return MessageType.UNKNOWN;
        }
        return switch (type.toLowerCase()) {
            case "text" -> MessageType.TEXT;
            case "image" -> MessageType.IMAGE;
            case "video" -> MessageType.VIDEO;
            case "audio" -> MessageType.AUDIO;
            case "document" -> MessageType.DOCUMENT;
            case "location" -> MessageType.LOCATION;
            case "contacts" -> MessageType.CONTACTS;
            case "reaction" -> MessageType.REACTION;
            case "unsupported" -> MessageType.UNSUPPORTED;
            default -> MessageType.UNKNOWN;
        };
    }

    /**
     * 解析消息状态字符串
     */
    private static MessageStatus parseMessageStatus(String status) {
        if (status == null) {
            return MessageStatus.UNKNOWN;
        }
        return switch (status.toLowerCase()) {
            case "sent" -> MessageStatus.SENT;
            case "delivered" -> MessageStatus.DELIVERED;
            case "read" -> MessageStatus.READ;
            default -> MessageStatus.UNKNOWN;
        };
    }

    // ==================== JSON 工具方法 ====================

    private static String getAsString(JsonObject obj, String key) {
        JsonElement element = obj.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        return element.getAsString();
    }

    /** 取字符串或数字为字符串（如 message.from 可能为数字） */
    private static String getAsStringOrNumber(JsonObject obj, String key) {
        JsonElement element = obj.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonPrimitive()) {
            var prim = element.getAsJsonPrimitive();
            if (prim.isString()) return prim.getAsString();
            if (prim.isNumber()) return String.valueOf(prim.getAsLong());
        }
        return element.getAsString();
    }

    private static long getAsLong(JsonObject obj, String key) {
        JsonElement element = obj.get(key);
        if (element == null || element.isJsonNull()) {
            return 0L;
        }
        // timestamp 可能是字符串格式
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            try {
                return Long.parseLong(element.getAsString());
            } catch (NumberFormatException e) {
                return 0L;
            }
        }
        return element.getAsLong();
    }

    private static Boolean getAsBoolean(JsonObject obj, String key) {
        JsonElement element = obj.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        return element.getAsBoolean();
    }

    private static Double getAsDouble(JsonObject obj, String key) {
        JsonElement element = obj.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            try {
                return Double.parseDouble(element.getAsString());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return element.getAsDouble();
    }
}
