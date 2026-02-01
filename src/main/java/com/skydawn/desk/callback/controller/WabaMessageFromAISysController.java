package com.skydawn.desk.callback.controller;

import com.skydawn.common.Defs;
import com.skydawn.desk.dto.GeneralMessageDto;
import com.skydawn.desk.message.waba.WabaToGeneralMessageConverter;
import com.skydawn.desk.service.CsAllocationService;
import com.skydawn.ingest.dto.WabaMessageDto;
import com.skydawn.ingest.parser.WabaParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 接收 Relay 转发过来的消息：解析 WABA，转为 GeneralMessageDto，按 fromId 分配/推送。
 */
@RestController
@RequestMapping("/message/callback/aisys")
public class WabaMessageFromAISysController {

    private static final Logger log = LoggerFactory.getLogger(WabaMessageFromAISysController.class);

    @Value("${sys.desk-http-head-token}")
    private String expectedToken;

    private final CsAllocationService csAllocationService;

    public WabaMessageFromAISysController(CsAllocationService csAllocationService) {
        this.csAllocationService = csAllocationService;
    }

    @PostMapping("/waba")
    public ResponseEntity<String> receiveMessage(
            @RequestHeader(value = Defs.HTTP_HEAD_TOKEN_NAME, required = false) String token,
            @RequestHeader(value = Defs.HTTP_HEAD_DATA_SOURCE_NAME, required = false) String dataSource,
            @RequestBody String body) {

        log.info("receiveMessage body: {}", body);
        if (token == null || !token.equals(expectedToken)) {
            log.warn("Token 校验失败, 收到的 token: {}", token);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid token");
        }

        WabaMessageDto dto = WabaParser.parseWabaMessage(body);
        if (dto == null) {
            log.warn("消息解析失败 - 数据来源: {}, 原始消息: {}", dataSource, body);
            return ResponseEntity.badRequest().body("Parse failed");
        }

        GeneralMessageDto general = WabaToGeneralMessageConverter.fromWaba(dto);
        try {
            csAllocationService.receiveMessage(general);
        } catch (Exception e) {
            log.error("receiveMessage route/push failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Route failed");
        }
        return ResponseEntity.ok("success");
    }
}
