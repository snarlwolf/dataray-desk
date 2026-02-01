package com.skydawn.desk.core.controller;

import com.skydawn.desk.message.waba.WabaMessageSender;
import com.skydawn.redis.RedisFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 客服台：通知 WABA 我方已读对方消息（对方可见“已读”状态）。
 * 与发送消息不同，使用 POST /desk/status/read。
 */
@RestController
@RequestMapping("/desk")
public class StatusMessageSenderController {

    private static final Logger log = LoggerFactory.getLogger(StatusMessageSenderController.class);

    private final RedisFinder redisFinder;
    private final WabaMessageSender wabaMessageSender;

    public StatusMessageSenderController(RedisFinder redisFinder, WabaMessageSender wabaMessageSender) {
        this.redisFinder = redisFinder;
        this.wabaMessageSender = wabaMessageSender;
    }

    /**
     * 标记对方消息为已读，通知 WABA 使对方知晓我方已阅读。
     * POST /desk/status/read  body: { "conversationId": "...", "messageIds": ["wamid.xxx", "wamid.yyy"] }
     */
    @PostMapping("/status/read")
    public ResponseEntity<Map<String, Object>> markAsRead(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        if (request.getSession().getAttribute(SysUserLoginController.SESSION_USER_KEY) == null) {
            result.put("success", false);
            result.put("message", "Not logged in");
            return ResponseEntity.ok(result);
        }
        String conversationId = body != null ? (String) body.get("conversationId") : null;
        @SuppressWarnings("unchecked")
        List<String> messageIds = body != null ? (List<String>) body.get("messageIds") : null;
        if (conversationId == null || conversationId.isBlank()) {
            result.put("success", false);
            result.put("message", "conversationId required");
            return ResponseEntity.ok(result);
        }
        if (messageIds == null || messageIds.isEmpty()) {
            result.put("success", true);
            result.put("message", "no messageIds to mark");
            return ResponseEntity.ok(result);
        }
        String type = redisFinder.getConversationType(conversationId);
        if (!"waba".equalsIgnoreCase(type)) {
            result.put("success", false);
            result.put("message", "Not a WABA conversation");
            return ResponseEntity.ok(result);
        }
        String phoneNumberId = redisFinder.getConversationPhone(conversationId);
        if (phoneNumberId == null || phoneNumberId.isBlank()) {
            result.put("success", false);
            result.put("message", "conversation phoneNumberId missing");
            return ResponseEntity.ok(result);
        }
        int ok = 0;
        for (String messageId : messageIds) {
            if (messageId != null && !messageId.isBlank() && wabaMessageSender.markAsRead(phoneNumberId, messageId)) {
                ok++;
            }
        }
        log.debug("markAsRead conversationId={} marked={}/{}", conversationId, ok, messageIds.size());
        result.put("success", ok > 0);
        result.put("marked", ok);
        result.put("total", messageIds.size());
        return ResponseEntity.ok(result);
    }
}
