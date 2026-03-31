package com.skydawn.desk.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.skydawn.desk.core.entity.SysUser;
import com.skydawn.desk.sockets.CsMessageSendSockets;
import com.skydawn.redis.CsRedisKeys;
import com.skydawn.redis.RedisFinder;
import com.skydawn.redis.RedisOperation;

/**
 * 在线同事通知服务：客服 WebSocket 上线/下线时，向同部门所有在线同事推送全量在线列表。
 *
 * <p>多实例部署策略：
 * <ol>
 *   <li>上线时：① 直接向连接本实例的当前用户推送全量列表（初次加载，立即可见已在线同事）；
 *              ② 发布 Redis Pub/Sub，所有实例各自向本地同部门连接推送更新后的列表。</li>
 *   <li>下线时：删除档案后发布 Redis Pub/Sub，其余实例推送更新后列表。</li>
 * </ol>
 *
 * <p>注：Pub/Sub 消息由 {@link com.skydawn.desk.sockets.CsColleaguesNotifyListener} 接收，
 * 转调 {@link #pushColleagueListToLocalDeptUsers(Long)}。
 */
@Service
public class CsColleagueNotifyService {

    private static final Logger log = LoggerFactory.getLogger(CsColleagueNotifyService.class);
    private static final Gson GSON = new Gson();

    private final RedisOperation redisOperation;
    private final RedisFinder redisFinder;
    private final CsMessageSendSockets sockets;

    public CsColleagueNotifyService(RedisOperation redisOperation, RedisFinder redisFinder,
                                    CsMessageSendSockets sockets) {
        this.redisOperation = redisOperation;
        this.redisFinder = redisFinder;
        this.sockets = sockets;
    }

    // ─── 对外接口 ─────────────────────────────────────────────────────────────

    /**
     * 用户 WebSocket 上线：
     * 1. 将档案（deptId/nickName/avatar）写入 Redis。
     * 2. 向本实例上的当前用户立即推送全量同事列表（初次加载）。
     * 3. 发布 Pub/Sub，通知所有实例更新同部门在线用户的同事列表。
     */
    public void onUserOnline(SysUser user) {
        if (user == null || user.getUserName() == null) return;
        String userName = user.getUserName();
        Long deptId = user.getDeptId();

        redisOperation.setUserProfile(userName, buildProfileJson(user));

        // 初次加载：直接向连接本实例的当前用户推送（不依赖 Pub/Sub 延迟）
        pushToSingleUser(userName, deptId);

        // 广播给其他实例（含本实例，本实例对自己是重复推送但无副作用）
        publishColleagueChange(deptId);
    }

    /**
     * 用户 WebSocket 下线：
     * 1. 删除 Redis 档案。
     * 2. 发布 Pub/Sub，通知所有实例更新同部门在线用户的同事列表。
     */
    public void onUserOffline(String userName, Long deptId) {
        if (userName == null || userName.isBlank()) return;
        redisOperation.deleteUserProfile(userName);
        publishColleagueChange(deptId);
    }

    /**
     * 由 Redis Pub/Sub 监听器调用：向本实例上同部门所有已连接用户推送全量在线列表。
     * 每个实例各自执行，确保多实例下所有在线用户均能收到通知。
     *
     * <p>当部门内已无在线成员时（列表为空），会额外执行两项操作：
     * <ol>
     *   <li>向本实例上仍有 profile 的同部门连接用户推送空列表（处理 HTTP 退出与 WS 关闭的竞态窗口）。</li>
     *   <li>清理该部门内有 profile key 但已不在线（无 users: key）的僵尸档案，防止实例崩溃后遗留脏数据。</li>
     * </ol>
     */
    public void pushColleagueListToLocalDeptUsers(Long deptId) {
        List<ColleagueInfo> deptOnline = buildDeptOnlineList(deptId);
        String payload = buildWsPayload(deptOnline);
        int pushed = 0;

        if (!deptOnline.isEmpty()) {
            for (ColleagueInfo info : deptOnline) {
                if (sockets.isOnline(info.userName)) {
                    sockets.sendToUser(info.userName, payload);
                    pushed++;
                }
            }
        } else {
            // 部门内已无在线成员：向本实例仍有 profile 的同部门用户推送空列表
            for (String localUser : sockets.getLocalUserNames()) {
                String profileJson = redisFinder.getUserProfile(localUser);
                if (profileJson == null) continue;
                ColleagueInfo info = parseProfile(localUser, profileJson);
                if (info != null && Objects.equals(info.deptId, deptId)) {
                    sockets.sendToUser(localUser, payload);
                    pushed++;
                }
            }
            // 清理该部门内的僵尸 profile key（有 profile 但无 users: key）
            cleanupStaleProfilesForDept(deptId);
        }

        log.debug("pushColleagueListToLocalDeptUsers: deptId={} deptOnlineCount={} localPushed={}",
                deptId, deptOnline.size(), pushed);
    }

    /**
     * 清理指定部门内的僵尸 profile key：用户档案存在但已不在 Redis 在线集合中（如实例崩溃后遗留）。
     */
    private void cleanupStaleProfilesForDept(Long deptId) {
        List<String> allProfileUsers = redisFinder.getAllProfileUserNames();
        for (String userName : allProfileUsers) {
            if (redisFinder.isUserOnline(userName)) continue;
            String profileJson = redisFinder.getUserProfile(userName);
            if (profileJson == null) continue;
            ColleagueInfo info = parseProfile(userName, profileJson);
            if (info == null || !Objects.equals(info.deptId, deptId)) continue;
            redisOperation.deleteUserProfile(userName);
            log.info("cleanupStaleProfile: removed stale profile userName={} deptId={}", userName, deptId);
        }
    }

    // ─── 私有实现 ─────────────────────────────────────────────────────────────

    /** 仅向指定用户推送（用于上线时的初次加载，该用户一定在本实例上） */
    private void pushToSingleUser(String userName, Long deptId) {
        List<ColleagueInfo> deptOnline = buildDeptOnlineList(deptId);
        String payload = buildWsPayload(deptOnline);
        sockets.sendToUser(userName, payload);
    }

    /** 向 Redis Pub/Sub 频道发布部门变化通知，消息内容为 deptId 或 "null" */
    private void publishColleagueChange(Long deptId) {
        String msg = deptId != null ? String.valueOf(deptId) : "null";
        redisOperation.publish(CsRedisKeys.CHANNEL_COLLEAGUES_NOTIFY, msg);
    }

    /**
     * 从 Redis 扫描所有在线且有档案的用户，筛选出同部门的列表（含会话数）。
     * deptId 为 null 时只返回同样无部门的用户。
     */
    private List<ColleagueInfo> buildDeptOnlineList(Long deptId) {
        List<String> onlineWithProfile = redisFinder.getOnlineUserNamesWithProfile();
        List<ColleagueInfo> result = new ArrayList<>();
        for (String userName : onlineWithProfile) {
            String profileJson = redisFinder.getUserProfile(userName);
            if (profileJson == null) continue;
            ColleagueInfo info = parseProfile(userName, profileJson);
            if (info == null) continue;
            if (!Objects.equals(info.deptId, deptId)) continue;
            info.conversationCount = redisFinder.getUserConversationCount(userName);
            result.add(info);
        }
        return result;
    }

    private String buildProfileJson(SysUser user) {
        JsonObject obj = new JsonObject();
        if (user.getDeptId() != null) obj.addProperty("deptId", user.getDeptId());
        obj.addProperty("nickName", user.getNickName() != null ? user.getNickName() : "");
        obj.addProperty("avatar", user.getAvatar() != null ? user.getAvatar() : "");
        return obj.toString();
    }

    private ColleagueInfo parseProfile(String userName, String profileJson) {
        try {
            JsonObject obj = JsonParser.parseString(profileJson).getAsJsonObject();
            ColleagueInfo info = new ColleagueInfo();
            info.userName = userName;
            info.deptId = obj.has("deptId") && !obj.get("deptId").isJsonNull()
                    ? obj.get("deptId").getAsLong() : null;
            info.nickName = obj.has("nickName") ? obj.get("nickName").getAsString() : "";
            info.avatar = obj.has("avatar") ? obj.get("avatar").getAsString() : "";
            return info;
        } catch (Exception e) {
            log.warn("parseProfile failed for userName={}", userName, e);
            return null;
        }
    }

    private String buildWsPayload(List<ColleagueInfo> list) {
        JsonObject root = new JsonObject();
        root.addProperty("kind", "colleagues");
        root.add("list", GSON.toJsonTree(list));
        return root.toString();
    }

    @SuppressWarnings("unused")
    private static class ColleagueInfo {
        String userName;
        Long deptId;
        String nickName;
        String avatar;
        int conversationCount;
    }
}
