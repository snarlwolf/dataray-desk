package com.skydawn.desk.callback.controller;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.skydawn.common.Defs;
import com.skydawn.ingest.dto.WabaMessageDto;
import com.skydawn.ingest.parser.WabaParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 接收 Relay 转发过来的消息
 * 注意：正式环境中这个接口不会接收任何消息，只是用来测试并解析waba消息的。正式环境过来的消息不会是waba格式。
 */
@RestController
@RequestMapping("/message/callback/relay")
public class WabaMessageFromRelayController {

    private static final Logger log = LoggerFactory.getLogger(WabaMessageFromRelayController.class);
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

    @Value("${sys.desk-http-head-token}")
    private String expectedToken;

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

        return ResponseEntity.ok("success");
    }
}
