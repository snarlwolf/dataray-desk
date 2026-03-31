package com.skydawn.desk.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.google.gson.Gson;
import com.skydawn.desk.core.converter.MessageToGeneralMessageConverter;
import com.skydawn.desk.core.entity.Conversation;
import com.skydawn.desk.core.entity.Message;
import com.skydawn.desk.core.mapper.ConversationMapper;
import com.skydawn.desk.core.mapper.MessageMapper;
import com.skydawn.desk.dto.GeneralMessageDto;
import com.skydawn.redis.CsRedisKeys;
import com.skydawn.redis.RedisFinder;
import com.skydawn.redis.RedisOperation;

/**
 * 会话恢复服务：当收到消息但 Redis 中无活跃会话时，检查数据库是否存在未关闭的历史会话，
 * 若存在则将历史消息和路由 key 恢复到 Redis，使后续 receiveMessage 能感知到该会话。
 */
@Service
public class CsSessionRestoreService {

    private static final Logger log = LoggerFactory.getLogger(CsSessionRestoreService.class);
    private static final Gson GSON = new Gson();
    /** 恢复时最多加载的历史消息条数 */
    private static final int RESTORE_HISTORY_LIMIT = 300;

    private final RedisFinder redisFinder;
    private final RedisOperation redisOperation;
    private final MessageMapper messageMapper;
    private final ConversationMapper conversationMapper;

    public CsSessionRestoreService(RedisFinder redisFinder,
                                    RedisOperation redisOperation,
                                    MessageMapper messageMapper,
                                    ConversationMapper conversationMapper) {
        this.redisFinder = redisFinder;
        this.redisOperation = redisOperation;
        this.messageMapper = messageMapper;
        this.conversationMapper = conversationMapper;
    }

    /**
     * 若 Redis 中已有活跃会话则直接返回；否则检查数据库是否存在未关闭的历史会话并恢复到 Redis。
     *
     * <p>恢复内容：
     * <ul>
     *   <li>conversation-type（供路由逻辑识别会话存在）</li>
     *   <li>conversation-phone（WABA 回复用 phoneNumberId）</li>
     *   <li>conversation-db-id（消息入库时的 conversation.id 缓存）</li>
     *   <li>conversation-messages（最近 {@value #RESTORE_HISTORY_LIMIT} 条历史消息 JSON）</li>
     * </ul>
     * conversation-user 不恢复，由 receiveMessage → allocateNewConversation 重新分配给在线客服。
     *
     * @param officialAccount phoneNumberId / 推广号
     * @param fromId          客户 ID（如手机号）
     */
    public void restoreIfNeeded(String officialAccount, String fromId) {
        if (officialAccount == null || officialAccount.isBlank() || fromId == null || fromId.isBlank()) {
            return;
        }
        String redisConvId = CsRedisKeys.formConversationId(officialAccount, fromId);

        // 步骤 1：Redis 中已有活跃会话（conversation-type key 存在），无需恢复
        if (redisFinder.getFromConversation(officialAccount, fromId) != null) {
            return;
        }

        // 使用与 CsAllocationService.receiveMessage 相同的锁 key，保证恢复与消息处理互斥，
        // 防止并发线程同时写同一会话的 Redis 字段（conversation-type/phone/db-id/messages）。
        String lockKey = CsRedisKeys.lockConversation(redisConvId);
        String lockVal = "restore-" + System.currentTimeMillis();
        if (!redisOperation.tryLock(lockKey, lockVal)) {
            log.debug("restoreIfNeeded: lock failed, another thread is restoring, redisConvId={}", redisConvId);
            return;
        }
        try {
            // 二次检查（加锁后可能另一线程已完成恢复）
            if (redisFinder.getFromConversation(officialAccount, fromId) != null) {
                return;
            }

            // 步骤 2：查找该客户在此推广号下最近一条 NORMAL 消息，定位上次会话
            Message latest = messageMapper.findLatestNormalByClientIdAndAccount(fromId, officialAccount);
            if (latest == null || latest.getConversationId() == null) {
                log.debug("restoreIfNeeded: no history found for fromId={} officialAccount={}", fromId, officialAccount);
                return;
            }

            Conversation conv = conversationMapper.findById(latest.getConversationId());
            if (conv == null) {
                log.debug("restoreIfNeeded: conversation record not found, id={}", latest.getConversationId());
                return;
            }
            // 已关闭的会话不恢复，让 receiveMessage 新建会话
            if ("2".equals(conv.getStatus())) {
                log.debug("restoreIfNeeded: conversation is CLOSED, will create new, redisConvId={}", redisConvId);
                return;
            }

            // 步骤 3：加载历史消息并恢复路由 key 到 Redis
            List<Message> history = messageMapper.findRecentByConversationId(latest.getConversationId(), RESTORE_HISTORY_LIMIT);

            redisOperation.setConversationType(redisConvId, "waba");
            redisOperation.setConversationPhone(redisConvId, officialAccount);
            redisOperation.setConversationDbId(redisConvId, conv.getId());

            for (Message msg : history) {
                GeneralMessageDto dto = MessageToGeneralMessageConverter.fromMessage(msg, redisConvId);
                redisOperation.appendConversationMessage(redisConvId, GSON.toJson(dto));
            }

            log.info("restoreIfNeeded: restored {} messages for redisConvId={}, convDbId={}",
                    history.size(), redisConvId, conv.getId());
        } finally {
            redisOperation.unlock(lockKey, lockVal);
        }
    }
}
