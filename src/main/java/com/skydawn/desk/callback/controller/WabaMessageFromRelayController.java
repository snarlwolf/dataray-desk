package com.skydawn.desk.callback.controller;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.skydawn.common.Defs;
import com.skydawn.desk.core.converter.GeneralMessageToMessageConverter;
import com.skydawn.redis.CsRedisKeys;
import com.skydawn.desk.core.entity.Message;
import com.skydawn.desk.core.mapper.MessageMapper;
import com.skydawn.desk.core.service.ConversationService;
import com.skydawn.desk.dto.GeneralMessageDto;
import com.skydawn.desk.message.waba.WabaToGeneralMessageConverter;
import com.skydawn.ingest.dto.WabaMessageDto;
import com.skydawn.ingest.parser.WabaParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 接收 Relay 转发过来的消息：仅入库 message 并打日志，不通知客服（不写 Redis、不推 WebSocket）。
 * 客服端消息由 /message/callback/aisys 等回调负责分配与推送。
 */
@RestController
@RequestMapping("/message/callback/relay")
public class WabaMessageFromRelayController {

    private static final Logger log = LoggerFactory.getLogger(WabaMessageFromRelayController.class);
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

    @Value("${sys.desk-http-head-token}")
    private String expectedToken;

    private final ConversationService conversationService;
    private final MessageMapper messageMapper;

    public WabaMessageFromRelayController(ConversationService conversationService, MessageMapper messageMapper) {
        this.conversationService = conversationService;
        this.messageMapper = messageMapper;
    }

    /**
     * 接收 Relay 回调消息
     *
     * @param token      请求头中的 Token
     * @param dataSource 请求头中的数据来源
     * @param body       请求体
     * @return 响应结果
     */
    @PostMapping("/waba")
    public ResponseEntity<String> receiveMessage(
            @RequestHeader(value = Defs.HTTP_HEAD_TOKEN_NAME, required = false) String token,
            @RequestHeader(value = Defs.HTTP_HEAD_DATA_SOURCE_NAME, required = false) String dataSource,
            @RequestBody String body) {

        // Token 校验
        if (token == null || !token.equals(expectedToken)) {
            log.warn("Token 校验失败, 收到的 token: {}", token);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid token");
        }

        // 解析消息
        WabaMessageDto dto = WabaParser.parseWabaMessage(body);
        if (dto == null) {
            log.warn("消息解析失败 - 数据来源: {}, 原始消息: {}", dataSource, body);
            return ResponseEntity.badRequest().body("Parse failed");
        }

        // 打印解析后的消息（JSON 格式，不含 rawJson 避免重复）
        String rawJson = dto.getRawJson();
        dto.setRawJson(null);  // 临时置空，避免日志过长
        log.info("收到消息:\n{}", gson.toJson(dto));
        dto.setRawJson(rawJson);  // 恢复

        // 转 GeneralMessageDto
        GeneralMessageDto general = WabaToGeneralMessageConverter.fromWaba(dto);
        // 入库用 redis 会话 id：用 account+clientId 拼装
        String redisConvId = general.getConversationId();
        if (redisConvId == null || redisConvId.isBlank()) {
            redisConvId = CsRedisKeys.formConversationId(general.getOfficialAccount(), general.getClientId());
        }
        general.setConversationId(redisConvId); // 供 toMessage 写入 message.redis_conversation_id
        Long convId = conversationService.getOrCreateByRedisConversationId(redisConvId, general.getOfficialAccount(), "waba");
        Message msg = GeneralMessageToMessageConverter.toMessage(general, convId, null);
        // STATUS 去重：同一回调被重复推送或多路转发时只入库一条，避免两条 DELIVERED 等
        boolean skipInsert = false;
        if (general.getMessageType() == GeneralMessageDto.MessageType.STATUS) {
            String srcId = general.getSourceMessageId() != null ? general.getSourceMessageId() : "";
            int exist = messageMapper.countByRedisConvIdAndSourceMessageIdAndTypeAndStatus(
                    redisConvId, srcId, "STATUS", general.getMessageStatus() != null ? general.getMessageStatus().name() : "");
            if (exist > 0) {
                log.debug("STATUS duplicate skip insert redisConvId={} sourceMessageId={} status={}", redisConvId, srcId, general.getMessageStatus());
                skipInsert = true;
            }
        }
        if (!skipInsert) {
            messageMapper.insert(msg);
        }

        return ResponseEntity.ok("success");
    }
}