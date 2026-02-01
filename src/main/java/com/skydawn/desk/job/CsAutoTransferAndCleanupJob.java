package com.skydawn.desk.job;

import com.skydawn.redis.CsRedisKeys;
import com.skydawn.redis.RedisFinder;
import com.skydawn.redis.RedisFinder.OnlineUserSlot;
import com.skydawn.redis.RedisOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * 定时任务：下线超时自动转移、孤儿会话清理、长期离线 user-conversation 清理。
 */
@Component
public class CsAutoTransferAndCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(CsAutoTransferAndCleanupJob.class);

    @Value("${sys.cs.auto-transfer-offline-minutes:30}")
    private int autoTransferOfflineMinutes;

    private final RedisFinder redisFinder;
    private final RedisOperation redisOperation;
    private final com.skydawn.desk.service.CsAllocationService csAllocationService;

    public CsAutoTransferAndCleanupJob(RedisFinder redisFinder, RedisOperation redisOperation,
                                       com.skydawn.desk.service.CsAllocationService csAllocationService) {
        this.redisFinder = redisFinder;
        this.redisOperation = redisOperation;
        this.csAllocationService = csAllocationService;
    }

    /** 每 5 分钟：下线超时自动转移 */
    @Scheduled(fixedDelayString = "${sys.timer.main-task-interval:40000}")
    public void autoTransferOfflineUsers() {
        List<String> offlineUserIds = redisFinder.getOfflineUserIdsWithConversations();
        long now = System.currentTimeMillis();
        for (String userId : offlineUserIds) {
            String offlineSince = redisFinder.getUserOfflineSince(userId);
            if (offlineSince == null) continue;
            long offlineTs = parseLoginTime(offlineSince);
            if (now - offlineTs < autoTransferOfflineMinutes * 60_000L) continue;
            String lockKey = CsRedisKeys.lockTransfer(userId);
            if (!redisOperation.tryLock(lockKey, "auto-" + now)) continue;
            try {
                Set<String> conversationIds = redisFinder.getUserConversationList(userId);
                for (String convId : conversationIds) {
                    String convLock = CsRedisKeys.lockConversation(convId);
                    if (!redisOperation.tryLock(convLock, "transfer-" + now)) continue;
                    try {
                        redisOperation.userConversationRemove(userId, convId);
                        List<OnlineUserSlot> slots = redisFinder.getOnlineUsersWithConversationCountAndLoginTime();
                        String target = csAllocationService.pickBestUser(slots);
                        if (target != null) {
                            redisOperation.userConversationAdd(target, convId);
                            redisOperation.setConversationUser(convId, target);
                        }
                    } finally {
                        redisOperation.unlock(convLock);
                    }
                }
                redisOperation.userConversationClear(userId);
                redisOperation.deleteUserOfflineSince(userId);
                log.info("autoTransfer userId={} conversations={}", userId, conversationIds.size());
            } finally {
                redisOperation.unlock(lockKey);
            }
        }
    }

    /** 每天凌晨：孤儿会话 key 清理 */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupOrphanConversationKeys() {
        List<String> orphans = redisFinder.findOrphanConversationIds();
        for (String convId : orphans) {
            String userId = redisFinder.getConversationUser(convId);
            if (userId != null) redisOperation.userConversationRemove(userId, convId);
            redisOperation.deleteConversationUser(convId);
            redisOperation.deleteConversationType(convId);
            redisOperation.deleteConversationPhone(convId);
            redisOperation.deleteConversationMessages(convId);
            log.info("cleanup orphan conversationId={}", convId);
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
