package com.skydawn.desk.core.controller;

import com.skydawn.desk.core.entity.SysUser;
import com.skydawn.desk.service.CsAllocationService;
import com.skydawn.redis.CsRedisKeys;
import com.skydawn.redis.RedisFinder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 客服台会话：结束会话、转移会话、拉取会话消息。Redis/WebSocket 使用客服登录名（userName）标识。
 */
@RestController
@RequestMapping("/desk")
public class CsConversationController {

    private final CsAllocationService csAllocationService;
    private final RedisFinder redisFinder;

    public CsConversationController(CsAllocationService csAllocationService, RedisFinder redisFinder) {
        this.csAllocationService = csAllocationService;
        this.redisFinder = redisFinder;
    }

    /**
     * 结束会话
     * POST /desk/conversation/end  body: { "conversationId": "..." }
     */
    @PostMapping("/conversation/end")
    public ResponseEntity<Map<String, Object>> endSession(@RequestBody Map<String, String> body, HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        SysUser user = (SysUser) request.getSession().getAttribute(SysUserLoginController.SESSION_USER_KEY);
        if (user == null) {
            result.put("success", false);
            result.put("message", "Not logged in");
            return ResponseEntity.ok(result);
        }
        String conversationId = body.get("conversationId");
        if (conversationId == null || conversationId.isBlank()) {
            result.put("success", false);
            result.put("message", "conversationId required");
            return ResponseEntity.ok(result);
        }
        String warning = csAllocationService.endSession(conversationId, user.getUserName());
        result.put("success", true);
        if (warning != null) {
            result.put("warning", warning);
        }
        return ResponseEntity.ok(result);
    }

    /**
     * 单会话转移
     * POST /desk/conversation/transfer  body: { "conversationId": "...", "toUserName": "..." }
     * toUserName 为目标客服的登录名（userName），前端从在线同事列表取得。
     */
    @PostMapping("/conversation/transfer")
    public ResponseEntity<Map<String, Object>> transfer(@RequestBody Map<String, String> body, HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        SysUser user = (SysUser) request.getSession().getAttribute(SysUserLoginController.SESSION_USER_KEY);
        if (user == null) {
            result.put("success", false);
            result.put("message", "Not logged in");
            return ResponseEntity.ok(result);
        }
        String conversationId = body.get("conversationId");
        String toUserName = body.get("toUserName");
        if (conversationId == null || toUserName == null || conversationId.isBlank() || toUserName.isBlank()) {
            result.put("success", false);
            result.put("message", "conversationId and toUserName required");
            return ResponseEntity.ok(result);
        }
        try {
            csAllocationService.transferSingle(conversationId, user.getUserName(), toUserName);
            result.put("success", true);
        } catch (IllegalArgumentException e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    /**
     * 当前用户的会话列表（刷新/重新登录后从 Redis user-conversation 拉取）
     * GET /desk/conversation/list
     * 返回 conversationIds 及简要信息：id 为会话 id，clientId 为解析出的客户标识。
     */
    @GetMapping("/conversation/list")
    public ResponseEntity<Map<String, Object>> getConversationList(HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        SysUser user = (SysUser) request.getSession().getAttribute(SysUserLoginController.SESSION_USER_KEY);
        if (user == null) {
            result.put("success", false);
            result.put("message", "Not logged in");
            return ResponseEntity.ok(result);
        }
        Set<String> ids = redisFinder.getUserConversationList(user.getUserName());
        List<Map<String, String>> conversations = ids.stream()
                .map(id -> {
                    String clientId = CsRedisKeys.parseFromIdFromConversationId(id);
                    return Map.<String, String>of("id", id, "clientId", clientId != null ? clientId : id);
                })
                .collect(Collectors.toList());
        result.put("success", true);
        result.put("conversationIds", List.copyOf(ids));
        result.put("conversations", conversations);
        return ResponseEntity.ok(result);
    }

    /**
     * 拉取会话消息（登录后通过 user-conversation 拿到会话列表，再按会话拉取消息）
     * GET /desk/conversation/messages?conversationId=...
     * 仅当当前客服为该会话归属人时返回消息列表（每条为 JSON，与推送格式一致）。
     */
    @GetMapping("/conversation/messages")
    public ResponseEntity<Map<String, Object>> getMessages(@RequestParam String conversationId, HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        SysUser user = (SysUser) request.getSession().getAttribute(SysUserLoginController.SESSION_USER_KEY);
        if (user == null) {
            result.put("success", false);
            result.put("message", "Not logged in");
            return ResponseEntity.ok(result);
        }
        if (conversationId == null || conversationId.isBlank()) {
            result.put("success", false);
            result.put("message", "conversationId required");
            return ResponseEntity.ok(result);
        }
        String owner = redisFinder.getConversationUser(conversationId);
        if (owner == null || !owner.equals(user.getUserName())) {
            result.put("success", false);
            result.put("message", "conversation not owned by you or not found");
            return ResponseEntity.ok(result);
        }
        List<String> messages = redisFinder.getConversationMessages(conversationId);
        result.put("success", true);
        result.put("messages", messages);
        return ResponseEntity.ok(result);
    }
}
