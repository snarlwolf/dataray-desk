package com.skydawn.redis;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 客服台使用的 Lua 脚本：原子分配、待分配队列入队/出队。
 */
public final class CsRedisScripts {

    private static final String REDIS_SCRIPTS_PATH = "redis/";

    /** 原子将会话分配给指定客服，返回 1=成功 0=该客服已满 */
    private static final RedisScript<Long> ASSIGN_CONVERSATION_TO_AGENT = loadScript("assign_conversation_to_agent.lua", Long.class);
    /** 待分配队列入队（去重），返回 1=新增 0=已存在 */
    private static final RedisScript<Long> PENDING_ADD = loadScript("pending_conversations_add.lua", Long.class);
    /** 待分配队列优先入队（队首，用于离线转移无目标时），返回 1=新增 0=已存在 */
    private static final RedisScript<Long> PENDING_ADD_PRIORITY = loadScript("pending_conversations_add_priority.lua", Long.class);
    /** 待分配队列批量出队（FIFO），返回 conversationId 列表 */
    @SuppressWarnings("unchecked")
    private static final RedisScript<List<String>> PENDING_POP_MULTI = (RedisScript<List<String>>) (RedisScript<?>) loadScript("pending_conversations_pop_multi.lua", List.class);

    public static RedisScript<Long> assignConversationToAgent() { return ASSIGN_CONVERSATION_TO_AGENT; }
    public static RedisScript<Long> pendingAdd() { return PENDING_ADD; }
    public static RedisScript<Long> pendingAddPriority() { return PENDING_ADD_PRIORITY; }
    public static RedisScript<List<String>> pendingPopMulti() { return PENDING_POP_MULTI; }

    private static <T> RedisScript<T> loadScript(String filename, Class<T> resultType) {
        try {
            String content = new ClassPathResource(REDIS_SCRIPTS_PATH + filename)
                    .getContentAsString(StandardCharsets.UTF_8);
            DefaultRedisScript<T> script = new DefaultRedisScript<>();
            script.setScriptText(content);
            script.setResultType(resultType);
            return script;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load Redis script: " + filename, e);
        }
    }

    private CsRedisScripts() {}
}
