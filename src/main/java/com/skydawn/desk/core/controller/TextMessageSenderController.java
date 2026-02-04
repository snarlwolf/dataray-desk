package com.skydawn.desk.core.controller;

import com.google.gson.Gson;
import com.skydawn.desk.core.entity.SysUser;
import com.skydawn.desk.dto.GeneralMessageDto;
import com.skydawn.desk.message.waba.WabaMessageSender;
import com.skydawn.desk.message.waba.WabaSenderDto;
import com.skydawn.redis.RedisFinder;
import com.skydawn.redis.RedisOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * 客服台对话回复：根据会话渠道（Redis conversationtype）选择发送方式；发送成功后将消息写入会话消息列表。
 */
@RestController
@RequestMapping("/desk")
public class TextMessageSenderController {

    private static final Logger log = LoggerFactory.getLogger(TextMessageSenderController.class);
    private static final Gson GSON = new Gson();

    private final RedisFinder redisFinder;
    private final RedisOperation redisOperation;
    private final WabaMessageSender wabaMessageSender;

    public TextMessageSenderController(RedisFinder redisFinder, RedisOperation redisOperation, WabaMessageSender wabaMessageSender) {
        this.redisFinder = redisFinder;
        this.redisOperation = redisOperation;
        this.wabaMessageSender = wabaMessageSender;
    }

    /**
     * 发送回复消息
     * POST /desk/message/send  body: GeneralMessageDto（conversationId、textBody 必填；fromId 可由后端从 Redis 取）
     */
    @PostMapping("/message/send")
    public ResponseEntity<Map<String, Object>> sendMessage(@RequestBody GeneralMessageDto dto, HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        if (request.getSession().getAttribute(SysUserLoginController.SESSION_USER_KEY) == null) {
            result.put("success", false);
            result.put("message", "Not logged in");
            return ResponseEntity.ok(result);
        }
        String conversationId = dto != null ? dto.getConversationId() : null;
        String textBody = dto != null ? dto.getTextBody() : null;
        if (conversationId == null || conversationId.isBlank()) {
            result.put("success", false);
            result.put("message", "conversationId required");
            return ResponseEntity.ok(result);
        }
        if (textBody == null) {
            textBody = "";
        }
        String type = redisFinder.getConversationType(conversationId);
        if ("waba".equalsIgnoreCase(type)) {
            String fromId = redisFinder.getConversationFromId(conversationId);
            String phoneNumberId = redisFinder.getConversationPhone(conversationId);
            if (fromId == null || fromId.isBlank() || phoneNumberId == null || phoneNumberId.isBlank()) {
                result.put("success", false);
                result.put("message", "conversation fromId or phoneNumberId missing");
                return ResponseEntity.ok(result);
            }
            WabaSenderDto wabaDto = new WabaSenderDto(fromId, textBody, phoneNumberId);
            if (dto != null && dto.getQuotedMessageId() != null && !dto.getQuotedMessageId().isBlank()) {
                wabaDto.setContextMessageId(dto.getQuotedMessageId());
            }
            var wabaResp = wabaMessageSender.sendWabaTextMessage(wabaDto);
            if (wabaResp != null) {
                result.put("success", true);
                if (wabaResp.getMessageId() != null) {
                    result.put("messageId", wabaResp.getMessageId());
                }
                GeneralMessageDto staffMsg = new GeneralMessageDto();
                staffMsg.setConversationId(conversationId);
                staffMsg.setFromId(fromId);
                staffMsg.setTextBody(textBody);
                staffMsg.setMessageId(wabaResp.getMessageId());
                staffMsg.setTimestamp(System.currentTimeMillis() / 1000);
                staffMsg.setMessageType(GeneralMessageDto.MessageType.TEXT);
                staffMsg.setMessageStatus(GeneralMessageDto.MessageStatus.SENT);
                staffMsg.setIsStaff(true);
                staffMsg.setMessageSource("waba");
                staffMsg.setPhoneNumberId(phoneNumberId);
                if (dto != null && dto.getQuotedMessageId() != null && !dto.getQuotedMessageId().isBlank()) {
                    staffMsg.setQuotedMessageId(dto.getQuotedMessageId());
                }
                Object sessionUser = request.getSession().getAttribute(SysUserLoginController.SESSION_USER_KEY);
                if (sessionUser instanceof SysUser u) {
                    staffMsg.setStaffLoginId(u.getUserName());
                }
                redisOperation.appendConversationMessage(conversationId, GSON.toJson(staffMsg));
            } else {
                log.warn("WABA send failed conversationId={}", conversationId);
                result.put("success", false);
                result.put("message", "WABA send failed");
            }
        } else {
            // 非 waba 暂不实现
            log.debug("Unsupported conversation type: {} conversationId={}", type, conversationId);
            result.put("success", false);
            result.put("message", "Unsupported conversation type: " + type);
        }
        return ResponseEntity.ok(result);
    }

    /**
     * 发送 Reaction（点赞表情）
     * POST /desk/message/reaction  body: { "conversationId": "...", "messageId": "wamid.xxx", "emoji": "👍" }
     */
    @PostMapping("/message/reaction")
    public ResponseEntity<Map<String, Object>> sendReaction(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        if (request.getSession().getAttribute(SysUserLoginController.SESSION_USER_KEY) == null) {
            result.put("success", false);
            result.put("message", "Not logged in");
            return ResponseEntity.ok(result);
        }
        String conversationId = body != null ? (String) body.get("conversationId") : null;
        String messageId = body != null ? (String) body.get("messageId") : null;
        String emoji = body != null && body.get("emoji") != null ? body.get("emoji").toString() : "👍";
        if (conversationId == null || conversationId.isBlank() || messageId == null || messageId.isBlank()) {
            result.put("success", false);
            result.put("message", "conversationId and messageId required");
            return ResponseEntity.ok(result);
        }
        String type = redisFinder.getConversationType(conversationId);
        if (!"waba".equalsIgnoreCase(type)) {
            result.put("success", false);
            result.put("message", "Unsupported conversation type");
            return ResponseEntity.ok(result);
        }
        String fromId = redisFinder.getConversationFromId(conversationId);
        String phoneNumberId = redisFinder.getConversationPhone(conversationId);
        if (fromId == null || fromId.isBlank() || phoneNumberId == null || phoneNumberId.isBlank()) {
            result.put("success", false);
            result.put("message", "conversation fromId or phoneNumberId missing");
            return ResponseEntity.ok(result);
        }
        boolean ok = wabaMessageSender.sendReaction(phoneNumberId, fromId, messageId, emoji);
        result.put("success", ok);
        if (!ok) {
            result.put("message", "WABA send reaction failed");
            return ResponseEntity.ok(result);
        }
        // 我方点赞也写入一条 kind=reaction，与对方点赞格式一致，重载/重登时能显示 emoji（否则只有 message_status 无 emoji）
        Map<String, Object> reactionPayload = new HashMap<>();
        reactionPayload.put("kind", "reaction");
        reactionPayload.put("conversationId", conversationId);
        reactionPayload.put("messageId", messageId);
        reactionPayload.put("emoji", emoji != null && !emoji.isEmpty() ? emoji : "👍");
        Object sessionUser = request.getSession().getAttribute(SysUserLoginController.SESSION_USER_KEY);
        if (sessionUser instanceof SysUser u) {
            reactionPayload.put("staffLoginId", u.getUserName());
        }
        redisOperation.appendConversationMessage(conversationId, GSON.toJson(reactionPayload));
        return ResponseEntity.ok(result);
    }
}
