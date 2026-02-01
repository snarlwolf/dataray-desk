package com.skydawn.desk.core.api;

import com.skydawn.desk.message.waba.WabaMessageSender;
import com.skydawn.desk.message.waba.WabaSenderDto;
import com.skydawn.ingest.dto.WabaMessageDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * WABA 文本消息发送接口（外部系统调用，需 HMAC 签名验证）
 */
@RestController
@RequestMapping("/message/sender")
public class WabaTextMessageSenderController {

    private static final Logger log = LoggerFactory.getLogger(WabaTextMessageSenderController.class);

    private final WabaMessageSender wabaMessageSender;

    public WabaTextMessageSenderController(WabaMessageSender wabaMessageSender) {
        this.wabaMessageSender = wabaMessageSender;
    }

    /**
     * 发送 WABA 文本消息
     *
     * @param phoneNumberId Phone Number ID
     * @param contentText   消息内容
     * @param targetNum     目标手机号
     * @return 发送结果
     */
    @PostMapping("/waba")
    public ResponseEntity<String> sendWabaMessage(
            @RequestParam String phoneNumberId,
            @RequestParam String contentText,
            @RequestParam String targetNum) {

        log.info("发送 WABA 消息 - phoneNumberId: {}, targetNum: {}, content: {}",
                phoneNumberId, targetNum, contentText);

        WabaSenderDto dto = new WabaSenderDto(targetNum, contentText, phoneNumberId);
        WabaMessageDto result = wabaMessageSender.sendWabaTextMessage(dto);

        if (result != null) {
            log.info("消息发送成功 - messageId: {}", result.getMessageId());
            return ResponseEntity.ok("success:" + result.getMessageId());
        } else {
            log.error("消息发送失败");
            return ResponseEntity.internalServerError().body("failed");
        }
    }
}
