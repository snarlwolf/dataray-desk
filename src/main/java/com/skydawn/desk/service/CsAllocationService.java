package com.skydawn.desk.service;

import com.google.gson.Gson;
import com.skydawn.common.Defs;
import com.skydawn.common.Vars;
import com.skydawn.desk.dto.GeneralMessageDto;
import com.skydawn.desk.message.waba.WabaMessageSender;
import com.skydawn.desk.message.waba.WabaSenderDto;
import com.skydawn.desk.sockets.CsMessageSendSockets;
import com.skydawn.ingest.dto.WabaMessageDto;
import com.skydawn.redis.CsRedisKeys;
import com.skydawn.redis.RedisFinder;
import com.skydawn.redis.RedisFinder.OnlineUserSlot;
import com.skydawn.redis.RedisOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 客服台会话分配、重新分配、结束会话、转移（无会话上限、无消息队列）。
 */
@Service
public class CsAllocationService {

    private static final Logger log = LoggerFactory.getLogger(CsAllocationService.class);
    private static final Gson GSON = new Gson();

    private static final String UNSUPPORTED_AUTO_REPLY_TEXT = "⚠️[系统消息] 暂不支持您所发送的消息格式！";
    private static final String UNSUPPORTED_DISPLAY_TEMPLATE = "对方发送了一条%s消息，目前不支持查看。";
    /** 保底：Vars 中无该消息类型配置时，工作台展示文案 */
    private static final String UNSUPPORTED_UNKNOWN_DISPLAY = "对方发送了一条不在我们系统内的消息类型，无法解析";
    /** 保底：Vars 中无该消息类型配置时，回复对方文案 */
    private static final String UNSUPPORTED_UNKNOWN_AUTO_REPLY = "⚠️[系统消息] 非常抱歉，由于未知原因，系统未能解析您所发送的这条消息！";
    private static final String END_SESSION_SYSTEM_MESSAGE = "⚠️[系统消息] 本次会话已经结束。";
    /** 未分配人工时的会话归属标识，新消息到达时若为此类则重新分配人工（规则同新会话） */
    private static final Set<String> NON_HUMAN_USER_IDS = Set.of("AISYSTEM", "TRANSFERING");

    private final RedisFinder redisFinder;
    private final RedisOperation redisOperation;
    private final CsMessageSendSockets sockets;
    private final WabaMessageSender wabaMessageSender;

    public CsAllocationService(RedisFinder redisFinder, RedisOperation redisOperation, CsMessageSendSockets sockets, WabaMessageSender wabaMessageSender) {
        this.redisFinder = redisFinder;
        this.redisOperation = redisOperation;
        this.sockets = sockets;
        this.wabaMessageSender = wabaMessageSender;
    }

    /**
     * 收到消息后：按会话 id（phoneNumberId-fromId）加锁，查会话 → 推送 / 重新分配 / 分配。
     */
    public void receiveMessage(GeneralMessageDto dto) {
        String fromId = dto.getFromId();
        if (fromId == null || fromId.isBlank()) {
            log.warn("receiveMessage: fromId empty, skip");
            return;
        }
        // 状态更新（SENT/DELIVERED/READ）只推送到已有会话
        if (dto.getMessageType() == GeneralMessageDto.MessageType.STATUS) {
            receiveStatusUpdate(dto);
            return;
        }
        // 点赞/反应：只推 kind=reaction 到已有会话
        if (dto.getMessageType() == GeneralMessageDto.MessageType.REACTION) {
            receiveReaction(dto);
            return;
        }
        // WhatsApp 消息：按 Vars.SYS_GLOBAL_PROPERTY 中 message.enable.{messageType} 判断是否支持；0=按“不支持”展示并自动回复
        if ("waba".equalsIgnoreCase(dto.getMessageSource())) {
            normalizeUnsupportedDisplay(dto);
        }
        String conversationId = CsRedisKeys.formConversationId(dto.getPhoneNumberId(), fromId);
        String lockKey = CsRedisKeys.lockConversation(conversationId);
        String lockVal = "msg-" + System.currentTimeMillis();
        if (!redisOperation.tryLock(lockKey, lockVal)) {
            try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            if (!redisOperation.tryLock(lockKey, lockVal)) {
                log.debug("receiveMessage: lock failed for conversationId={}, skip", conversationId);
                return;
            }
        }
        try {
            receiveMessageUnderLock(conversationId, dto);
        } finally {
            redisOperation.unlock(lockKey);
        }
    }

    /**
     * 状态更新（SENT/DELIVERED/READ）：仅推送到已有会话。
     * 推送专用 payload（kind=message_status），避免前端当成三条新消息展示；
     * 前端根据 messageId 找到“我方发送”的那条消息，只更新勾选状态。
     */
    private void receiveStatusUpdate(GeneralMessageDto dto) {
        String fromId = dto.getFromId();
        String existingConvId = redisFinder.getFromConversation(dto.getPhoneNumberId(), fromId);
        if (existingConvId == null) {
            log.debug("status update ignored, no conversation for fromId={}", fromId);
            return;
        }
        String messageId = dto.getMessageId();
        if (messageId == null || messageId.isBlank()) {
            log.debug("status update ignored, no messageId");
            return;
        }
        Map<String, Object> statusPayload = Map.of(
                "kind", "message_status",
                "conversationId", existingConvId,
                "messageId", messageId,
                "messageStatus", dto.getMessageStatus().name());
        String json = GSON.toJson(statusPayload);
        String userId = redisFinder.getConversationUser(existingConvId);
        if (userId != null && redisFinder.isUserOnline(userId)) {
            if (!sockets.sendToUser(userId, json)) {
                log.warn("push status update failed userId={}", userId);
            }
        }
        // 无论客服是否在线都写入 Redis，重登/刷新后拉取消息时能显示最新送达/已读状态
        redisOperation.appendConversationMessage(existingConvId, json);
    }

    /**
     * 点赞/反应：推 kind=reaction 到已有会话，前端在原消息右下方显示 emoji。
     */
    private void receiveReaction(GeneralMessageDto dto) {
        String fromId = dto.getFromId();
        String reactionMessageId = dto.getReactionMessageId();
        String emoji = dto.getReactionEmoji();
        if (reactionMessageId == null || reactionMessageId.isBlank()) {
            log.debug("reaction ignored, reactionMessageId empty");
            return;
        }
        String existingConvId = redisFinder.getFromConversation(dto.getPhoneNumberId(), fromId);
        if (existingConvId == null) {
            log.debug("reaction ignored, no conversation for fromId={}", fromId);
            return;
        }
        Map<String, Object> payload = Map.of(
                "kind", "reaction",
                "conversationId", existingConvId,
                "messageId", reactionMessageId,
                "emoji", emoji != null ? emoji : ""
        );
        String json = GSON.toJson(payload);
        String userId = redisFinder.getConversationUser(existingConvId);
        if (userId != null && sockets.isOnline(userId)) {
            if (!sockets.sendToUser(userId, json)) {
                log.warn("push reaction failed userId={}", userId);
            }
        }
        redisOperation.appendConversationMessage(existingConvId, json);
    }

    private void receiveMessageUnderLock(String conversationId, GeneralMessageDto dto) {
        String fromId = dto.getFromId();
        String existingConvId = redisFinder.getFromConversation(dto.getPhoneNumberId(), fromId);
        if (existingConvId == null) {
            if (!isContentMessageForNewSession(dto)) {
                log.debug("receiveMessage: ignore status-only message for new conversationId={}, no session created", conversationId);
                return;
            }
            allocateNewConversation(fromId, conversationId, dto);
            return;
        }
        String userId = redisFinder.getConversationUser(existingConvId);
        if (userId == null || redisFinder.getConversationFromId(existingConvId) == null) {
            if (!isContentMessageForNewSession(dto)) {
                log.debug("receiveMessage: ignore status-only message for orphan conversationId={}, no session", conversationId);
                return;
            }
            allocateNewConversation(fromId, conversationId, dto);
            return;
        }
        // 会话归属为 AISYSTEM/TRANSFERING 时视为未分配人工，重新分配给在线客服（规则同新会话）；客服打开会话时会从 Redis 拉取全部历史消息
        if (NON_HUMAN_USER_IDS.contains(userId)) {
            reallocateFromNonHumanUser(fromId, existingConvId, userId, dto);
            return;
        }
        if (redisFinder.isUserOnline(userId)) {
            pushToUser(userId, existingConvId, dto);
            return;
        }
        reassignAndPush(fromId, existingConvId, userId, dto);
    }

    /** 会话归属为 AISYSTEM/TRANSFERING 时重新分配给在线人工客服，分配规则同新会话。 */
    private void reallocateFromNonHumanUser(String fromId, String conversationId, String nonHumanUserId, GeneralMessageDto dto) {
        redisOperation.userConversationRemove(nonHumanUserId, conversationId);
        List<OnlineUserSlot> slots = redisFinder.getOnlineUsersWithConversationCountAndLoginTime();
        String newUserId = pickBestUser(slots);
        if (newUserId == null) {
            redisOperation.userConversationAdd(nonHumanUserId, conversationId);
            log.debug("reallocateFromNonHumanUser: no online user for conversationId={}, leave with {}", conversationId, nonHumanUserId);
            return;
        }
        redisOperation.userConversationAdd(newUserId, conversationId);
        redisOperation.setConversationUser(conversationId, newUserId);
        pushToUser(newUserId, conversationId, dto);
    }

    /**
     * 统一“不支持”展示与保底：根据 Vars.SYS_GLOBAL_PROPERTY 中 message.enable.{messageType} 决定：
     * "1"=正常接收；"0"=展示「对方发送了一条XXX消息，目前不支持查看。」并回复「暂不支持…」；
     * 无配置=保底展示「对方发送了一条不在我们系统内的消息类型，无法解析」并回复「非常抱歉，由于未知原因…」。
     */
    private void normalizeUnsupportedDisplay(GeneralMessageDto dto) {
        GeneralMessageDto.MessageType type = dto.getMessageType();
        if (type == null) return;
        String enableValue = getMessageEnableConfigValue(dto);
        if ("1".equals(enableValue)) return;  // 明确开启，正常接收
        dto.setMessageStatus(GeneralMessageDto.MessageStatus.UNSUPPORTED);
        if (enableValue == null) {
            // 保底：无配置
            dto.setTextBody(UNSUPPORTED_UNKNOWN_DISPLAY);
            dto.setUnsupportedAutoReplyText(UNSUPPORTED_UNKNOWN_AUTO_REPLY);
        } else {
            // "0"：已知类型但不支持
            dto.setTextBody(String.format(UNSUPPORTED_DISPLAY_TEMPLATE, type.name()));
        }
    }

    /** 从 Vars.SYS_GLOBAL_PROPERTY 取 key=Defs.PROP_KEY_MESSAGE_ENABLE_PRE + messageType；返回 "1"/"0"/null（无配置）。 */
    private static String getMessageEnableConfigValue(GeneralMessageDto dto) {
        Map<String, String> props = Vars.getSysGlobalProperty();
        if (props == null || props.isEmpty()) return null;
        String key = Defs.PROP_KEY_MESSAGE_ENABLE_PRE + dto.getMessageType().name();
        return props.get(key);
    }

    /** 仅 NORMAL、UNSUPPORTED 可建立会话；SENT/DELIVERED/READ 为首条时说明会话异常，忽略直到收到内容消息。 */
    private static boolean isContentMessageForNewSession(GeneralMessageDto dto) {
        GeneralMessageDto.MessageStatus s = dto.getMessageStatus();
        return s == GeneralMessageDto.MessageStatus.NORMAL || s == GeneralMessageDto.MessageStatus.UNSUPPORTED;
    }

    private void allocateNewConversation(String fromId, String conversationId, GeneralMessageDto dto) {
        List<OnlineUserSlot> slots = redisFinder.getOnlineUsersWithConversationCountAndLoginTime();
        String targetUserId = pickBestUser(slots);
        if (targetUserId == null) {
            log.debug("allocateNewConversation: no online user for fromId={}, skip", fromId);
            return;
        }
        redisOperation.userConversationAdd(targetUserId, conversationId);
        redisOperation.setConversationUser(conversationId, targetUserId);
        if (dto.getMessageSource() != null && !dto.getMessageSource().isBlank()) {
            redisOperation.setConversationType(conversationId, dto.getMessageSource());
            if ("waba".equalsIgnoreCase(dto.getMessageSource()) && dto.getPhoneNumberId() != null && !dto.getPhoneNumberId().isBlank()) {
                redisOperation.setConversationPhone(conversationId, dto.getPhoneNumberId());
            }
        }
        pushToUser(targetUserId, conversationId, dto);
    }

    private void reassignAndPush(String fromId, String conversationId, String oldUserId, GeneralMessageDto dto) {
        String lockKey = CsRedisKeys.lockConversation(conversationId);
        String lockVal = "reassign-" + System.currentTimeMillis();
        if (!redisOperation.tryLock(lockKey, lockVal)) {
            log.debug("reassignAndPush: lock failed for conversationId={}, skip", conversationId);
            return;
        }
        try {
            redisOperation.userConversationRemove(oldUserId, conversationId);
            List<OnlineUserSlot> slots = redisFinder.getOnlineUsersWithConversationCountAndLoginTime();
            String newUserId = pickBestUser(slots);
            if (newUserId == null) {
                redisOperation.userConversationAdd(oldUserId, conversationId);
                log.debug("reassignAndPush: no online user for conversationId={}, leave with oldUserId", conversationId);
                return;
            }
            redisOperation.userConversationAdd(newUserId, conversationId);
            redisOperation.setConversationUser(conversationId, newUserId);
            pushToUser(newUserId, conversationId, dto);
        } finally {
            redisOperation.unlock(lockKey);
        }
    }

    /** 分配规则：会话数最少、登录时间最晚、随机（无上限）。 */
    public String pickBestUser(List<OnlineUserSlot> slots) {
        if (slots == null || slots.isEmpty()) return null;
        if (slots.size() == 1) return slots.get(0).userId();
        int sameCount = slots.get(0).conversationCount();
        long sameCountCount = slots.stream().filter(s -> s.conversationCount() == sameCount).count();
        if (sameCountCount == 1) return slots.get(0).userId();
        int idx = ThreadLocalRandom.current().nextInt((int) sameCountCount);
        return slots.get(idx).userId();
    }

    private void pushToUser(String userId, String conversationId, GeneralMessageDto dto) {
        dto.setConversationId(conversationId);
        if (dto.getMessageSource() != null && !dto.getMessageSource().isBlank()) {
            redisOperation.setConversationType(conversationId, dto.getMessageSource());
            if ("waba".equalsIgnoreCase(dto.getMessageSource()) && dto.getPhoneNumberId() != null && !dto.getPhoneNumberId().isBlank()) {
                redisOperation.setConversationPhone(conversationId, dto.getPhoneNumberId());
            }
        }
        String json = GSON.toJson(dto);
        if (!sockets.sendToUser(userId, json)) {
            log.warn("pushToUser failed, userId={}", userId);
        }
        redisOperation.appendConversationMessage(conversationId, json);
        if (dto.getMessageStatus() == GeneralMessageDto.MessageStatus.UNSUPPORTED && "waba".equalsIgnoreCase(redisFinder.getConversationType(conversationId))) {
            sendUnsupportedAutoReplyAndPush(userId, conversationId, dto);
        }
    }

    /**
     * 收到 unsupported 消息时：向客户发送「暂不支持您所发送的消息格式！」，并作为我方消息推送到工作台（更深蓝气泡）。
     */
    private void sendUnsupportedAutoReplyAndPush(String userId, String conversationId, GeneralMessageDto incomingDto) {
        String fromId = incomingDto.getFromId();
        if (fromId == null || fromId.isBlank()) {
            log.warn("sendUnsupportedAutoReply: fromId empty");
            return;
        }
        String phoneNumberId = incomingDto.getPhoneNumberId();
        if (phoneNumberId == null || phoneNumberId.isBlank()) {
            phoneNumberId = redisFinder.getConversationPhone(conversationId);
        }
        if (phoneNumberId == null || phoneNumberId.isBlank()) {
            log.warn("sendUnsupportedAutoReply: phoneNumberId empty conversationId={}", conversationId);
            return;
        }
        String replyText = (incomingDto.getUnsupportedAutoReplyText() != null && !incomingDto.getUnsupportedAutoReplyText().isBlank())
                ? incomingDto.getUnsupportedAutoReplyText() : UNSUPPORTED_AUTO_REPLY_TEXT;
        WabaSenderDto wabaDto = new WabaSenderDto(fromId, replyText, phoneNumberId);
        String incomingMessageId = incomingDto.getMessageId();
        if (incomingMessageId != null && !incomingMessageId.isBlank()) {
            wabaDto.setContextMessageId(incomingMessageId);
        }
        WabaMessageDto sent = wabaMessageSender.sendWabaTextMessage(wabaDto);
        if (sent == null) {
            log.warn("sendUnsupportedAutoReply: WABA send failed conversationId={}", conversationId);
            return;
        }
        GeneralMessageDto replyDto = new GeneralMessageDto();
        replyDto.setConversationId(conversationId);
        replyDto.setFromId(fromId);
        replyDto.setTextBody(replyText);
        replyDto.setMessageId(sent.getMessageId());
        replyDto.setTimestamp(System.currentTimeMillis() / 1000);
        replyDto.setMessageType(GeneralMessageDto.MessageType.TEXT);
        replyDto.setMessageStatus(GeneralMessageDto.MessageStatus.SENT);
        replyDto.setIsStaff(true);
        replyDto.setIsSystemReply(true);
        replyDto.setSenderName(userId);
        replyDto.setQuotedMessageId(incomingDto.getMessageId());
        replyDto.setMessageSource("waba");
        replyDto.setPhoneNumberId(phoneNumberId);
        String replyJson = GSON.toJson(replyDto);
        if (!sockets.sendToUser(userId, replyJson)) {
            log.warn("pushToUser system reply failed, userId={}", userId);
        }
        redisOperation.appendConversationMessage(conversationId, replyJson);
    }

    /**
     * 结束会话：conversationId → 查 fromId/userId，删除相关 key（含 conversation-messages），删除后若为 WABA 则向客户发送系统消息。
     */
    public void endSession(String conversationId, String currentUserId) {
        String fromId = redisFinder.getConversationFromId(conversationId);
        String userId = redisFinder.getConversationUser(conversationId);
        if (fromId == null || userId == null) {
            log.info("endSession: conversation already gone, conversationId={}", conversationId);
            return;
        }
        String type = redisFinder.getConversationType(conversationId);
        String phoneNumberId = redisFinder.getConversationPhone(conversationId);
        redisOperation.endSessionAtomic(fromId, userId, conversationId);
        log.info("endSession conversationId={} fromId={} userId={}", conversationId, fromId, userId);
        if ("waba".equalsIgnoreCase(type) && phoneNumberId != null && !phoneNumberId.isBlank() && fromId != null) {
            WabaSenderDto dto = new WabaSenderDto(fromId, END_SESSION_SYSTEM_MESSAGE, phoneNumberId);
            WabaMessageDto sent = wabaMessageSender.sendWabaTextMessage(dto);
            if (sent != null) {
                log.info("endSession: sent system message to customer fromId={}", fromId);
            } else {
                log.warn("endSession: failed to send system message to customer fromId={}", fromId);
            }
        }
    }

    /**
     * 单会话转移：conversationId 从 A 转给 B。校验当前归属为 fromUserId。
     */
    public void transferSingle(String conversationId, String fromUserId, String toUserId) {
        String currentOwner = redisFinder.getConversationUser(conversationId);
        if (currentOwner == null || !currentOwner.equals(fromUserId)) {
            throw new IllegalArgumentException("conversation not owned by you");
        }
        if (!redisFinder.isUserOnline(toUserId)) {
            throw new IllegalArgumentException("toUserId not online");
        }
        redisOperation.userConversationRemove(fromUserId, conversationId);
        redisOperation.userConversationAdd(toUserId, conversationId);
        redisOperation.setConversationUser(conversationId, toUserId);
    }

}
