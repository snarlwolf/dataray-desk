package com.skydawn.desk.core.controller;

import com.google.gson.Gson;
import com.skydawn.desk.core.converter.GeneralMessageToMessageConverter;
import com.skydawn.desk.core.entity.Message;
import com.skydawn.desk.core.entity.SysUser;
import com.skydawn.desk.core.mapper.MessageMapper;
import com.skydawn.desk.core.service.ConversationService;
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
    private final ConversationService conversationService;
    private final MessageMapper messageMapper;

    public TextMessageSenderController(RedisFinder redisFinder, RedisOperation redisOperation, WabaMessageSender wabaMessageSender,
                                      ConversationService conversationService, MessageMapper messageMapper) {
        this.redisFinder = redisFinder;
        this.redisOperation = redisOperation;
        this.wabaMessageSender = wabaMessageSender;
        this.conversationService = conversationService;
        this.messageMapper = messageMapper;
    }

    /**
     * 发送回复消息
     * POST /desk/message/send  body: GeneralMessageDto（conversationId、textBody 必填；clientId 可由后端从 Redis 取）
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
            String clientId = redisFinder.getConversationFromId(conversationId);
            String officialAccount = redisFinder.getConversationPhone(conversationId);
            if (clientId == null || clientId.isBlank() || officialAccount == null || officialAccount.isBlank()) {
                result.put("success", false);
                result.put("message", "conversation clientId or officialAccount missing");
                return ResponseEntity.ok(result);
            }
            WabaSenderDto wabaDto = new WabaSenderDto(clientId, textBody, officialAccount);
            if (dto != null && dto.getReferencedMessageId() != null && !dto.getReferencedMessageId().isBlank()) {
                wabaDto.setContextMessageId(dto.getReferencedMessageId());
            }
            var wabaResp = wabaMessageSender.sendWabaTextMessage(wabaDto);
            if (wabaResp != null) {
                result.put("success", true);
                if (wabaResp.getMessageId() != null) {
                    result.put("messageId", wabaResp.getMessageId());
                }
                GeneralMessageDto staffMsg = new GeneralMessageDto();
                staffMsg.setConversationId(conversationId);
                staffMsg.setClientId(clientId);
                staffMsg.setClientName(dto != null ? dto.getClientName() : null);
                staffMsg.setTextBody(textBody);
                staffMsg.setSourceMessageId(wabaResp.getMessageId());
                staffMsg.setTimestamp(System.currentTimeMillis() / 1000);
                staffMsg.setMessageType(GeneralMessageDto.MessageType.TEXT);
                staffMsg.setMessageStatus(GeneralMessageDto.MessageStatus.SENT);
                staffMsg.setIsStaff(true);
                staffMsg.setMessageSource("waba");
                staffMsg.setOfficialAccount(officialAccount);
                if (dto != null && dto.getReferencedMessageId() != null && !dto.getReferencedMessageId().isBlank()) {
                    staffMsg.setReferencedMessageId(dto.getReferencedMessageId());
                }
                Object sessionUser = request.getSession().getAttribute(SysUserLoginController.SESSION_USER_KEY);
                String staffUserName = null;
                if (sessionUser instanceof SysUser u) {
                    staffMsg.setCsStaffName(u.getUserName());
                    staffUserName = u.getUserName();
                }
                redisOperation.appendConversationMessage(conversationId, GSON.toJson(staffMsg));
                // 客服回复同时入库 message
                Long convId = conversationService.getOrCreateByRedisConversationId(conversationId, officialAccount, "waba");
                if (convId != null) {
                    Message msg = GeneralMessageToMessageConverter.toMessage(staffMsg, convId, null);
                    msg.setIsStaff(1);
                    msg.setSysUserId(staffUserName);
                    messageMapper.insert(msg);
                } else {
                    log.warn("sendMessage: convId is null, skip insert, conversationId={}", conversationId);
                }
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
        String clientId = redisFinder.getConversationFromId(conversationId);
        String officialAccount = redisFinder.getConversationPhone(conversationId);
        if (clientId == null || clientId.isBlank() || officialAccount == null || officialAccount.isBlank()) {
            result.put("success", false);
            result.put("message", "conversation clientId or officialAccount missing");
            return ResponseEntity.ok(result);
        }
        boolean ok = wabaMessageSender.sendReaction(officialAccount, clientId, messageId, emoji);
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
            reactionPayload.put("csStaffName", u.getUserName());
        }
        redisOperation.appendConversationMessage(conversationId, GSON.toJson(reactionPayload));
        return ResponseEntity.ok(result);
    }
}
