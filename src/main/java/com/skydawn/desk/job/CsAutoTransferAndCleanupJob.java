package com.skydawn.desk.job;

import com.skydawn.common.Defs;
import com.skydawn.common.Vars;
import com.skydawn.redis.CsRedisKeys;
import com.skydawn.redis.RedisFinder;
import com.skydawn.redis.RedisOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 定时任务：下线超时自动转移（阈值来自 Vars desk.unline.keep-conversation 秒）、孤儿会话清理。
 */
@Component
public class CsAutoTransferAndCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(CsAutoTransferAndCleanupJob.class);
    /** 客服离线多少秒后自动转移其会话，默认 500 秒 */
    private static final int DEFAULT_OFFLINE_KEEP_CONVERSATION_SECONDS = 500;

    private final RedisFinder redisFinder;
    private final RedisOperation redisOperation;
    private final com.skydawn.desk.service.CsAllocationService csAllocationService;

    public CsAutoTransferAndCleanupJob(RedisFinder redisFinder, RedisOperation redisOperation,
                                       com.skydawn.desk.service.CsAllocationService csAllocationService) {
        this.redisFinder = redisFinder;
        this.redisOperation = redisOperation;
        this.csAllocationService = csAllocationService;
    }

    /** 按固定间隔：下线超时自动转移（阈值：Vars desk.unline.keep-conversation 秒，默认 500） */
    @Scheduled(fixedDelayString = "${sys.timer.main-task-interval:40000}")
    public void autoTransferOfflineUsers() {
        int thresholdSeconds = getOfflineKeepConversationSeconds();
        List<String> offlineUserIds = redisFinder.getOfflineUserIdsWithConversations();
        long now = System.currentTimeMillis();
        for (String userId : offlineUserIds) {
            String offlineSince = redisFinder.getUserOfflineSince(userId);
            if (offlineSince == null) continue;
            long offlineTs = parseLoginTime(offlineSince);
            if (now - offlineTs < thresholdSeconds * 1000L) continue;
            String lockKey = CsRedisKeys.lockTransfer(userId);
            String outerLockVal = "auto-" + now;
            if (!redisOperation.tryLock(lockKey, outerLockVal)) continue;
            try {
                Set<String> conversationIds = redisFinder.getUserConversationList(userId);
                for (String convId : conversationIds) {
                    String convLock = CsRedisKeys.lockConversation(convId);
                    String innerLockVal = "transfer-" + now;
                    if (!redisOperation.tryLock(convLock, innerLockVal)) continue;
                    try {
                        redisOperation.userConversationRemove(userId, convId);
                        redisOperation.decrLoadZset(userId);
                        int maxCount = csAllocationService.getMaxConvCount();
                        boolean assigned = false;
                        for (String target : redisFinder.getOrderedCandidateUserIdsForAssignment(maxCount)) {
                            if (redisOperation.assignConversationToAgent(target, convId, maxCount)) {
                                assigned = true;
                                break;
                            }
                        }
                        if (!assigned) {
                            redisOperation.setConversationUser(convId, "AISYSTEM");
                            redisOperation.pendingConversationsAddPriority(convId);
                        }
                    } finally {
                        redisOperation.unlock(convLock, innerLockVal);
                    }
                }
                redisOperation.userConversationClear(userId);
                redisOperation.deleteUserOfflineSince(userId);
                log.info("autoTransfer userId={} conversations={}", userId, conversationIds.size());
            } finally {
                redisOperation.unlock(lockKey, outerLockVal);
            }
        }
    }

    /** 每天凌晨：孤儿会话 key 清理（并同步负载 ZSET） */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupOrphanConversationKeys() {
        List<String> orphans = redisFinder.findOrphanConversationIds();
        for (String convId : orphans) {
            String userId = redisFinder.getConversationUser(convId);
            if (userId != null) {
                redisOperation.userConversationRemove(userId, convId);
                redisOperation.decrLoadZset(userId);
            }
            redisOperation.deleteConversationUser(convId);
            redisOperation.deleteConversationType(convId);
            redisOperation.deleteConversationPhone(convId);
            redisOperation.deleteConversationMessages(convId);
            log.info("cleanup orphan conversationId={}", convId);
        }
    }

    private int getOfflineKeepConversationSeconds() {
        Map<String, String> props = Vars.getSysGlobalProperty();
        if (props == null || props.isEmpty()) return DEFAULT_OFFLINE_KEEP_CONVERSATION_SECONDS;
        String v = props.get(Defs.PROP_KEY_DESK_OFFLINE_KEEP_CONVERSATION);
        if (v == null || v.isBlank()) return DEFAULT_OFFLINE_KEEP_CONVERSATION_SECONDS;
        try {
            return Math.max(0, Integer.parseInt(v.trim()));
        } catch (NumberFormatException e) {
            return DEFAULT_OFFLINE_KEEP_CONVERSATION_SECONDS;
        }
    }

    private long parseLoginTime(String yyyyMMddHHmmss) {
        try {
            int y = Integer.parseInt(yyyyMMddHHmmss.substring(0, 4));
            int M = Integer.parseInt(yyyyMMddHHmmss.substring(4, 6));
            int d = Integer.parseInt(yyyyMMddHHmmss.substring(6, 8));
            int H = Integer.parseInt(yyyyMMddHHmmss.substring(8, 10));
            int m = Integer.parseInt(yyyyMMddHHmmss.substring(10, 12));
            int s = Integer.parseInt(yyyyMMddHHmmss.substring(12, 14));
            return java.time.LocalDateTime.of(y, M, d, H, m, s)
                    .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (Exception e) {
            return 0;
        }
    }
}
