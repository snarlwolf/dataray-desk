package com.skydawn.desk.service;

import com.google.gson.Gson;
import com.skydawn.common.Defs;
import com.skydawn.common.Vars;
import com.skydawn.desk.core.converter.GeneralMessageToMessageConverter;
import com.skydawn.desk.core.entity.Message;
import com.skydawn.desk.core.mapper.MessageMapper;
import com.skydawn.desk.core.service.ConversationService;
import com.skydawn.desk.dto.GeneralMessageDto;
import com.skydawn.desk.message.waba.WabaMessageSender;
import com.skydawn.desk.message.waba.WabaSenderDto;
import com.skydawn.desk.sockets.CsMessageSendSockets;
import com.skydawn.ingest.dto.WabaMessageDto;
import com.skydawn.redis.CsRedisKeys;
import com.skydawn.redis.RedisFinder;
import com.skydawn.redis.RedisOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 客服台会话分配、重新分配、结束会话、转移；支持每客服会话上限与待分配队列。
 */
@Service
public class CsAllocationService {

    private static final Logger log = LoggerFactory.getLogger(CsAllocationService.class);
    /** 客服最大同时会话数默认值 */
    private static final int DEFAULT_MAX_CONV_COUNT = 20;
    /** 登录/结束会话时每次从待分配队列最多拉取条数，避免独占 */
    private static final int PENDING_PULL_MAX_PER_ACTION = 5;
    private static final Gson GSON = new Gson();

    private static final String UNSUPPORTED_AUTO_REPLY_TEXT =
            "⚠️[System Message] The message format you sent is not supported yet.";

    private static final String UNSUPPORTED_DISPLAY_TEMPLATE =
            "🚫 The other party sent a %s message, which is not supported for viewing.";

        /** 保底：Vars 中无该消息类型配置时，工作台展示文案 */
    private static final String UNSUPPORTED_UNKNOWN_DISPLAY =
            "🚫 The other party sent a message type that is not recognized by our system.";

        /** 保底：Vars 中无该消息类型配置时，回复对方文案 */
    private static final String UNSUPPORTED_UNKNOWN_AUTO_REPLY =
            "⚠️[System Message] Sorry, we couldn't process your message due to an unknown reason.";

    private static final String END_SESSION_SYSTEM_MESSAGE =
            "⚠️[System Message] This session has ended.";

    /** 未分配人工时的会话归属标识，新消息到达时若为此类则重新分配人工（规则同新会话） */
    private static final Set<String> NON_HUMAN_USER_IDS = Set.of("AISYSTEM", "TRANSFERING");

    /**
     * 先入 Redis 会话记录再通知 WebSocket，避免因断联丢消息。
     * 确保会话 type/phone 已设置，并将消息 JSON 追加到 conversation-messages。
     */
    private void ensureConversationAndAppendMessage(String conversationId, GeneralMessageDto dto) {
        dto.setConversationId(conversationId);
        if (dto.getMessageSource() != null && !dto.getMessageSource().isBlank()) {
            redisOperation.setConversationType(conversationId, dto.getMessageSource());
            if ("waba".equalsIgnoreCase(dto.getMessageSource()) && dto.getOfficialAccount() != null && !dto.getOfficialAccount().isBlank()) {
                redisOperation.setConversationPhone(conversationId, dto.getOfficialAccount());
            }
        }
        String json = GSON.toJson(dto);
        redisOperation.appendConversationMessage(conversationId, json);
    }

    private final RedisFinder redisFinder;
    private final RedisOperation redisOperation;
    private final CsMessageSendSockets sockets;
    private final WabaMessageSender wabaMessageSender;
    private final ConversationService conversationService;
    private final MessageMapper messageMapper;

    public CsAllocationService(RedisFinder redisFinder, RedisOperation redisOperation, CsMessageSendSockets sockets, WabaMessageSender wabaMessageSender,
                               ConversationService conversationService, MessageMapper messageMapper) {
        this.redisFinder = redisFinder;
        this.redisOperation = redisOperation;
        this.sockets = sockets;
        this.wabaMessageSender = wabaMessageSender;
        this.conversationService = conversationService;
        this.messageMapper = messageMapper;
    }

    /**
     * 收到消息后：按会话 id（officialAccount-clientId）加锁，查会话 → 推送 / 重新分配 / 分配。
     */
    public void receiveMessage(GeneralMessageDto dto) {
        String clientId = dto.getClientId();
        if (clientId == null || clientId.isBlank()) {
            log.warn("receiveMessage: clientId empty, skip");
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
        String conversationId = CsRedisKeys.formConversationId(dto.getOfficialAccount(), clientId);
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
        String clientId = dto.getClientId();
        String existingConvId = redisFinder.getFromConversation(dto.getOfficialAccount(), clientId);
        if (existingConvId == null) {
            log.debug("status update ignored, no conversation for clientId={}", clientId);
            return;
        }
        String sourceMessageId = dto.getSourceMessageId();
        if (sourceMessageId == null || sourceMessageId.isBlank()) {
            log.debug("status update ignored, no sourceMessageId");
            return;
        }
        Map<String, Object> statusPayload = Map.of(
                "kind", "message_status",
                "conversationId", existingConvId,
                "messageId", sourceMessageId,
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
        String clientId = dto.getClientId();
        String referencedMessageId = dto.getReferencedMessageId();
        String emoji = dto.getReactionEmoji();
        if (referencedMessageId == null || referencedMessageId.isBlank()) {
            log.debug("reaction ignored, referencedMessageId empty");
            return;
        }
        String existingConvId = redisFinder.getFromConversation(dto.getOfficialAccount(), clientId);
        if (existingConvId == null) {
            log.debug("reaction ignored, no conversation for clientId={}", clientId);
            return;
        }
        Map<String, Object> payload = Map.of(
                "kind", "reaction",
                "conversationId", existingConvId,
                "messageId", referencedMessageId,
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
        String clientId = dto.getClientId();
        String existingConvId = redisFinder.getFromConversation(dto.getOfficialAccount(), clientId);
        if (existingConvId == null) {
            if (!isContentMessageForNewSession(dto)) {
                log.debug("receiveMessage: ignore status-only message for new conversationId={}, no session created", conversationId);
                return;
            }
            allocateNewConversation(clientId, conversationId, dto);
            return;
        }
        String userId = redisFinder.getConversationUser(existingConvId);
        if (userId == null || redisFinder.getConversationFromId(existingConvId) == null) {
            if (!isContentMessageForNewSession(dto)) {
                log.debug("receiveMessage: ignore status-only message for orphan conversationId={}, no session", conversationId);
                return;
            }
            allocateNewConversation(clientId, conversationId, dto);
            return;
        }
        // 会话归属为 AISYSTEM/TRANSFERING 时视为未分配人工，重新分配给在线客服（规则同新会话）；客服打开会话时会从 Redis 拉取全部历史消息
        if (NON_HUMAN_USER_IDS.contains(userId)) {
            reallocateFromNonHumanUser(clientId, existingConvId, userId, dto);
            return;
        }
        if (redisFinder.isUserOnline(userId)) {
            pushToUser(userId, existingConvId, dto);
            return;
        }
        reassignAndPush(clientId, existingConvId, userId, dto);
    }

    /** 会话归属为 AISYSTEM/TRANSFERING 时重新分配给在线人工客服，或加入待分配队列。 */
    private void reallocateFromNonHumanUser(String fromId, String conversationId, String nonHumanUserId, GeneralMessageDto dto) {
        redisOperation.userConversationRemove(nonHumanUserId, conversationId);
        ensureConversationAndAppendMessage(conversationId, dto);
        int maxCount = getMaxConvCount();
        List<String> candidates = redisFinder.getOrderedCandidateUserIdsForAssignment(maxCount);
        for (String candidateId : candidates) {
            if (redisOperation.assignConversationToAgent(candidateId, conversationId, maxCount)) {
                sendMessageToUserOnly(candidateId, conversationId, dto);
                return;
            }
        }
        if (redisOperation.pendingConversationsAdd(conversationId)) {
            log.debug("reallocateFromNonHumanUser: no suitable agent, conversationId={} enqueued", conversationId);
        }
        // conversation-user 保持 AISYSTEM，不再加回 AISYSTEM 的 user-conversation，避免负载统计失真
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

    /** 从 Vars 取 desk.conversation.max-count，默认 {@value #DEFAULT_MAX_CONV_COUNT}（供定时任务等调用） */
    public int getMaxConvCount() {
        Map<String, String> props = Vars.getSysGlobalProperty();
        if (props == null || props.isEmpty()) return DEFAULT_MAX_CONV_COUNT;
        String v = props.get(Defs.PROP_KEY_DESK_CONVERSATION_MAX_COUNT);
        if (v == null || v.isBlank()) return DEFAULT_MAX_CONV_COUNT;
        try {
            return Math.max(1, Integer.parseInt(v.trim()));
        } catch (NumberFormatException e) {
            return DEFAULT_MAX_CONV_COUNT;
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
        ensureConversationAndAppendMessage(conversationId, dto);
        int maxCount = getMaxConvCount();
        List<String> candidates = redisFinder.getOrderedCandidateUserIdsForAssignment(maxCount);
        for (String candidateId : candidates) {
            if (redisOperation.assignConversationToAgent(candidateId, conversationId, maxCount)) {
                sendMessageToUserOnly(candidateId, conversationId, dto);
                return;
            }
        }
        // 无合适客服或均已满：标记为 AISYSTEM 并加入待分配队列（不加入 AISYSTEM 的 user-conversation）
        redisOperation.setConversationUser(conversationId, "AISYSTEM");
        if (redisOperation.pendingConversationsAdd(conversationId)) {
            log.debug("allocateNewConversation: no suitable agent for fromId={}, conversationId={} enqueued", fromId, conversationId);
        }
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
            redisOperation.decrLoadZset(oldUserId);
            ensureConversationAndAppendMessage(conversationId, dto);
            int maxCount = getMaxConvCount();
            List<String> candidates = redisFinder.getOrderedCandidateUserIdsForAssignment(maxCount);
            for (String candidateId : candidates) {
                if (redisOperation.assignConversationToAgent(candidateId, conversationId, maxCount)) {
                    sendMessageToUserOnly(candidateId, conversationId, dto);
                    return;
                }
            }
            redisOperation.setConversationUser(conversationId, "AISYSTEM");
            redisOperation.pendingConversationsAdd(conversationId);
            log.debug("reassignAndPush: no suitable agent for conversationId={}, enqueued", conversationId);
        } finally {
            redisOperation.unlock(lockKey);
        }
    }

    /** 分配用：取负载最小且最先登录的一名候选客服（供定时任务等使用）。 */
    public String pickBestCandidateUserId() {
        List<String> candidates = redisFinder.getOrderedCandidateUserIdsForAssignment(getMaxConvCount());
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    /**
     * 将消息写入 Redis 并推送到客服 WebSocket（用于「已有归属、直接推送」路径，只写一次）。
     */
    private void pushToUser(String userId, String conversationId, GeneralMessageDto dto) {
        ensureConversationAndAppendMessage(conversationId, dto);
        sendMessageToUserOnly(userId, conversationId, dto);
    }

    /**
     * 仅推送到客服 WebSocket（不写 Redis）。用于调用方已通过 ensureConversationAndAppendMessage 写入过的场景，避免同一条消息被追加两次。
     */
    private void sendMessageToUserOnly(String userId, String conversationId, GeneralMessageDto dto) {
        String json = GSON.toJson(dto);
        if (!sockets.sendToUser(userId, json)) {
            log.warn("sendMessageToUserOnly failed, userId={}", userId);
        }
        if (dto.getMessageStatus() == GeneralMessageDto.MessageStatus.UNSUPPORTED && "waba".equalsIgnoreCase(redisFinder.getConversationType(conversationId))) {
            sendUnsupportedAutoReplyAndPush(userId, conversationId, dto);
        }
    }

    /**
     * 收到 unsupported 消息时：向客户发送「暂不支持您所发送的消息格式！」，并作为我方消息推送到工作台（更深蓝气泡）。
     */
    private void sendUnsupportedAutoReplyAndPush(String userId, String conversationId, GeneralMessageDto incomingDto) {
        String clientId = incomingDto.getClientId();
        if (clientId == null || clientId.isBlank()) {
            log.warn("sendUnsupportedAutoReply: clientId empty");
            return;
        }
        String officialAccount = incomingDto.getOfficialAccount();
        if (officialAccount == null || officialAccount.isBlank()) {
            officialAccount = redisFinder.getConversationPhone(conversationId);
        }
        if (officialAccount == null || officialAccount.isBlank()) {
            log.warn("sendUnsupportedAutoReply: officialAccount empty conversationId={}", conversationId);
            return;
        }
        String replyText = (incomingDto.getUnsupportedAutoReplyText() != null && !incomingDto.getUnsupportedAutoReplyText().isBlank())
                ? incomingDto.getUnsupportedAutoReplyText() : UNSUPPORTED_AUTO_REPLY_TEXT;
        WabaSenderDto wabaDto = new WabaSenderDto(clientId, replyText, officialAccount);
        String incomingSourceMessageId = incomingDto.getSourceMessageId();
        if (incomingSourceMessageId != null && !incomingSourceMessageId.isBlank()) {
            wabaDto.setContextMessageId(incomingSourceMessageId);
        }
        WabaMessageDto sent = wabaMessageSender.sendWabaTextMessage(wabaDto);
        if (sent == null) {
            log.warn("sendUnsupportedAutoReply: WABA send failed conversationId={}", conversationId);
            return;
        }
        GeneralMessageDto replyDto = new GeneralMessageDto();
        replyDto.setConversationId(conversationId);
        replyDto.setClientId(clientId);
        replyDto.setTextBody(replyText);
        replyDto.setSourceMessageId(sent.getMessageId());
        replyDto.setTimestamp(System.currentTimeMillis() / 1000);
        replyDto.setMessageType(GeneralMessageDto.MessageType.TEXT);
        replyDto.setMessageStatus(GeneralMessageDto.MessageStatus.SENT);
        replyDto.setIsStaff(true);
        replyDto.setIsSystemReply(true);
        replyDto.setCsStaffName(userId);
        replyDto.setReferencedMessageId(incomingDto.getSourceMessageId());
        replyDto.setMessageSource("waba");
        replyDto.setOfficialAccount(officialAccount);
        String replyJson = GSON.toJson(replyDto);
        redisOperation.appendConversationMessage(conversationId, replyJson);
        // 自动回复入库 message，sys_user_id='AUTO'
        Long convId = conversationService.getOrCreateByRedisConversationId(conversationId, officialAccount, "waba");
        Message msg = GeneralMessageToMessageConverter.toMessage(replyDto, convId, null);
        msg.setIsStaff(1);
        msg.setSysUserId("AUTO");
        messageMapper.insert(msg);
        if (!sockets.sendToUser(userId, replyJson)) {
            log.warn("pushToUser system reply failed, userId={}", userId);
        }
    }

    /**
     * 结束会话：删除相关 key、负载 -1，若为 WABA 则向客户发系统消息；然后从待分配队列拉取最多 5 条会话给当前客服。
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
        if ("waba".equalsIgnoreCase(type) && phoneNumberId != null && !phoneNumberId.isBlank() && fromId != null) {
            WabaSenderDto dto = new WabaSenderDto(fromId, END_SESSION_SYSTEM_MESSAGE, phoneNumberId);
            WabaMessageDto sent = wabaMessageSender.sendWabaTextMessage(dto);
            if (sent != null) {
                log.info("endSession: sent system message to customer fromId={}", fromId);
                // 会话结束自动回复入库 message，sys_user_id='AUTO'
                GeneralMessageDto endDto = new GeneralMessageDto();
                endDto.setConversationId(conversationId);
                endDto.setClientId(fromId);
                endDto.setTextBody(END_SESSION_SYSTEM_MESSAGE);
                endDto.setSourceMessageId(sent.getMessageId());
                endDto.setTimestamp(System.currentTimeMillis() / 1000);
                endDto.setMessageType(GeneralMessageDto.MessageType.TEXT);
                endDto.setMessageStatus(GeneralMessageDto.MessageStatus.SENT);
                endDto.setIsStaff(true);
                endDto.setMessageSource("waba");
                endDto.setOfficialAccount(phoneNumberId);
                Long convId = conversationService.getOrCreateByRedisConversationId(conversationId, phoneNumberId, "waba");
                Message msg = GeneralMessageToMessageConverter.toMessage(endDto, convId, null);
                msg.setIsStaff(1);
                msg.setSysUserId("AUTO");
                messageMapper.insert(msg);
            } else {
                log.warn("endSession: failed to send system message to customer fromId={}", fromId);
            }
        }
        redisOperation.endSessionAtomic(fromId, userId, conversationId);
        log.info("endSession conversationId={} fromId={} userId={}", conversationId, fromId, userId);
        tryPullPendingToAgent(currentUserId);
    }

    /** 结束会话或登录后：从待分配队列拉取最多 {@value #PENDING_PULL_MAX_PER_ACTION} 条分配给该客服并推送。 */
    public void tryPullPendingToAgent(String userId) {
        int maxCount = getMaxConvCount();
        int currentLoad = redisFinder.getLoadScore(userId);
        int room = Math.max(0, maxCount - currentLoad);
        if (room == 0) return;
        int pullCount = Math.min(PENDING_PULL_MAX_PER_ACTION, room);
        List<String> popped = redisOperation.pendingConversationsPopMulti(pullCount);
        if (popped.isEmpty()) return;
        List<String> assigned = new java.util.ArrayList<>();
        for (String cid : popped) {
            if (redisOperation.assignConversationToAgent(userId, cid, maxCount)) {
                assigned.add(cid);
            } else {
                redisOperation.pendingConversationsAdd(cid);
            }
        }
        if (!assigned.isEmpty()) {
            pushAssignedConversationsToAgent(userId, assigned);
        }
    }

    /** 登录后调用：初始化该客服负载 ZSET，并从待分配队列拉取最多 min(5, maxCount-当前数) 条分配并推送。 */
    public void pullPendingConversationsForAgent(String userId) {
        redisOperation.initUserConversationIfAbsent(userId);
        int currentCount = redisFinder.getUserConversationCount(userId);
        redisOperation.setLoadZsetScore(userId, currentCount);
        tryPullPendingToAgent(userId);
    }

    private void pushAssignedConversationsToAgent(String userId, List<String> conversationIds) {
        Map<String, Object> payload = Map.of("kind", "conversations_assigned", "conversationIds", conversationIds);
        String json = GSON.toJson(payload);
        if (!sockets.sendToUser(userId, json)) {
            log.warn("pushAssignedConversationsToAgent failed userId={}", userId);
        }
    }

    /**
     * 单会话转移：conversationId 从 A 转给 B。校验当前归属为 fromUserId；同步负载 ZSET。
     */
    public void transferSingle(String conversationId, String fromUserId, String toUserId) {
        String currentOwner = redisFinder.getConversationUser(conversationId);
        if (currentOwner == null || !currentOwner.equals(fromUserId)) {
            throw new IllegalArgumentException("conversation not owned by you");
        }
        if (!redisFinder.isUserOnline(toUserId)) {
            throw new IllegalArgumentException("toUserId not online");
        }
        int maxCount = getMaxConvCount();
        if (redisFinder.getLoadScore(toUserId) >= maxCount) {
            throw new IllegalArgumentException("toUserId has reached max conversation count");
        }
        redisOperation.userConversationRemove(fromUserId, conversationId);
        redisOperation.userConversationAdd(toUserId, conversationId);
        redisOperation.setConversationUser(conversationId, toUserId);
        redisOperation.decrLoadZset(fromUserId);
        redisOperation.incrLoadZset(toUserId);
    }

}
