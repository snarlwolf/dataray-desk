package com.skydawn.desk.message.waba;

/**
 * WABA 消息发送 DTO
 */
public class WabaSenderDto {

    /** 消息产品类型，固定为 "whatsapp" */
    private String messagingProduct = "whatsapp";

    /** 接收者手机号（带国家码，如 601163697608） */
    private String to;

    /** 消息类型，如 "text" */
    private String type = "text";

    /** 文本消息内容 */
    private String textBody;

    /** Phone Number ID（用于构建 API URL） */
    private String phoneNumberId;

    /** 回复某条消息时的原消息 ID（context.message_id） */
    private String contextMessageId;

    public WabaSenderDto() {
    }

    public WabaSenderDto(String to, String textBody, String phoneNumberId) {
        this.to = to;
        this.textBody = textBody;
        this.phoneNumberId = phoneNumberId;
    }

    // ==================== Getters and Setters ====================

    public String getMessagingProduct() {
        return messagingProduct;
    }

    public void setMessagingProduct(String messagingProduct) {
        this.messagingProduct = messagingProduct;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTextBody() {
        return textBody;
    }

    public void setTextBody(String textBody) {
        this.textBody = textBody;
    }

    public String getPhoneNumberId() {
        return phoneNumberId;
    }

    public void setPhoneNumberId(String phoneNumberId) {
        this.phoneNumberId = phoneNumberId;
    }

    public String getContextMessageId() {
        return contextMessageId;
    }

    public void setContextMessageId(String contextMessageId) {
        this.contextMessageId = contextMessageId;
    }

    /**
     * 构建发送到 WABA API 的 JSON 请求体（支持 context 回复）
     */
    public String toRequestJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"messaging_product\":\"").append(escapeJson(messagingProduct))
          .append("\",\"to\":\"").append(escapeJson(to != null ? to : "")).append("\"");
        if (contextMessageId != null && !contextMessageId.isBlank()) {
            sb.append(",\"context\":{\"message_id\":\"").append(escapeJson(contextMessageId)).append("\"}");
        }
        sb.append(",\"type\":\"").append(escapeJson(type))
          .append("\",\"text\":{\"body\":\"").append(escapeJson(textBody))
          .append("\"}}");
        return sb.toString();
    }

    /**
     * 简单的 JSON 字符串转义
     */
    private String escapeJson(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
