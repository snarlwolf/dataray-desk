package com.skydawn.desk.message.waba;

import com.google.gson.Gson;
import com.skydawn.ingest.dto.WabaMessageDto;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * WABA 消息发送器
 */
@Component
public class WabaMessageSender {

    private static final Logger log = LoggerFactory.getLogger(WabaMessageSender.class);
    private static final Gson GSON = new Gson();

    @Value("${waba.authorization}")
    private String authorization;

    @Value("${waba.api-url}")
    private String apiUrlTemplate;

    /**
     * 发送 WABA 文本消息
     *
     * @param dto 发送参数
     * @return 解析后的响应 DTO，发送失败返回 null
     */
    public WabaMessageDto sendWabaTextMessage(WabaSenderDto dto) {
        if (dto == null || dto.getTo() == null || dto.getTextBody() == null) {
            log.error("发送参数不完整");
            return null;
        }

        // 构建 API URL（替换 phoneNumberId）
        String apiUrl = apiUrlTemplate.replace("{phoneNumId}", dto.getPhoneNumberId());
        String requestBody = dto.toRequestJson();

        log.info("发送 WABA 消息 - URL: {}, To: {}", apiUrl, dto.getTo());
        log.debug("请求体: {}", requestBody);

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(apiUrl);
            httpPost.setHeader("Authorization", authorization);
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));

            return httpClient.execute(httpPost, response -> {
                int statusCode = response.getCode();
                String responseBody = EntityUtils.toString(response.getEntity());

                if (statusCode >= 200 && statusCode < 300) {
                    log.info("WABA 消息发送成功 - 状态码: {}", statusCode);
                    log.debug("响应: {}", responseBody);

                    // 尝试解析响应（WABA 发送响应格式与接收消息不同，这里直接返回简单的 DTO）
                    return buildResponseDto(dto, responseBody);
                } else {
                    log.error("WABA 消息发送失败 - 状态码: {}, 响应: {}", statusCode, responseBody);
                    return null;
                }
            });

        } catch (Exception e) {
            log.error("发送 WABA 消息异常: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 构建响应 DTO
     * WABA 发送成功响应示例:
     * {
     *   "messaging_product": "whatsapp",
     *   "contacts": [{"input": "601163697608", "wa_id": "601163697608"}],
     *   "messages": [{"id": "wamid.HBgM..."}]
     * }
     */
    private WabaMessageDto buildResponseDto(WabaSenderDto request, String responseBody) {
        WabaMessageDto dto = new WabaMessageDto();
        dto.setMessageType(WabaMessageDto.MessageType.TEXT);
        dto.setMessageStatus(WabaMessageDto.MessageStatus.SENT);
        dto.setTextBody(request.getTextBody());
        dto.setRecipientId(request.getTo());
        dto.setRawJson(responseBody);

        // 尝试从响应中提取 message id
        try {
            // 简单解析，提取 wamid
            int idStart = responseBody.indexOf("\"id\":");
            if (idStart > 0) {
                int valueStart = responseBody.indexOf("\"", idStart + 5) + 1;
                int valueEnd = responseBody.indexOf("\"", valueStart);
                if (valueStart > 0 && valueEnd > valueStart) {
                    dto.setMessageId(responseBody.substring(valueStart, valueEnd));
                }
            }
        } catch (Exception e) {
            log.warn("解析响应 message id 失败: {}", e.getMessage());
        }

        return dto;
    }

    /**
     * 标记消息为已读，通知 WABA 使对方知晓我方已阅读。
     * POST /PHONE_NUMBER_ID/messages  body: {"messaging_product":"whatsapp","status":"read","message_id":"..."}
     *
     * @param phoneNumberId WABA 电话号码 ID
     * @param messageId     WABA 消息 ID（来自 webhook 的 message.id）
     * @return 是否成功
     */
    public boolean markAsRead(String phoneNumberId, String messageId) {
        if (phoneNumberId == null || phoneNumberId.isBlank() || messageId == null || messageId.isBlank()) {
            log.warn("markAsRead: phoneNumberId or messageId empty");
            return false;
        }
        String apiUrl = apiUrlTemplate.replace("{phoneNumId}", phoneNumberId);
        String requestBody = GSON.toJson(java.util.Map.of(
                "messaging_product", "whatsapp",
                "status", "read",
                "message_id", messageId));

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(apiUrl);
            httpPost.setHeader("Authorization", authorization);
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));

            return httpClient.execute(httpPost, response -> {
                int statusCode = response.getCode();
                if (statusCode >= 200 && statusCode < 300) {
                    log.debug("WABA markAsRead success messageId={}", messageId);
                    return true;
                }
                String body = EntityUtils.toString(response.getEntity());
                log.warn("WABA markAsRead failed statusCode={} messageId={} body={}", statusCode, messageId, body);
                return false;
            });
        } catch (Exception e) {
            log.error("WABA markAsRead exception messageId={}", messageId, e);
            return false;
        }
    }

    /**
     * 发送 Reaction（点赞表情）到 WABA。
     * POST /PHONE_NUMBER_ID/messages  body: {"messaging_product":"whatsapp","to":"...","type":"reaction","reaction":{"message_id":"...","emoji":"👍"}}
     */
    public boolean sendReaction(String phoneNumberId, String to, String messageId, String emoji) {
        if (phoneNumberId == null || phoneNumberId.isBlank() || to == null || to.isBlank()
                || messageId == null || messageId.isBlank()) {
            log.warn("sendReaction: phoneNumberId, to or messageId empty");
            return false;
        }
        if (emoji == null) emoji = "👍";
        String apiUrl = apiUrlTemplate.replace("{phoneNumId}", phoneNumberId);
        String requestBody = GSON.toJson(java.util.Map.of(
                "messaging_product", "whatsapp",
                "to", to,
                "type", "reaction",
                "reaction", java.util.Map.of("message_id", messageId, "emoji", emoji)));

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(apiUrl);
            httpPost.setHeader("Authorization", authorization);
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));

            return httpClient.execute(httpPost, response -> {
                int statusCode = response.getCode();
                if (statusCode >= 200 && statusCode < 300) {
                    log.debug("WABA sendReaction success messageId={}", messageId);
                    return true;
                }
                String body = EntityUtils.toString(response.getEntity());
                log.warn("WABA sendReaction failed statusCode={} messageId={} body={}", statusCode, messageId, body);
                return false;
            });
        } catch (Exception e) {
            log.error("WABA sendReaction exception messageId={}", messageId, e);
            return false;
        }
    }
}
