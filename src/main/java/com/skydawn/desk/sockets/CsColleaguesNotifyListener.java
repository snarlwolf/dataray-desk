package com.skydawn.desk.sockets;

import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import com.skydawn.desk.service.CsColleagueNotifyService;

/**
 * 订阅 Redis 频道 {@code desk:cs:colleagues:notify}：
 * 收到某部门有客服上线/下线的广播后，向本实例上该部门所有已连接用户推送全量在线同事列表。
 * 每个实例各自处理，实现多实例下的实时同步。
 */
@Component
public class CsColleaguesNotifyListener implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(CsColleaguesNotifyListener.class);

    private final CsColleagueNotifyService colleagueNotifyService;

    public CsColleaguesNotifyListener(CsColleagueNotifyService colleagueNotifyService) {
        this.colleagueNotifyService = colleagueNotifyService;
    }

    @Override
    public void onMessage(@NonNull Message message, @Nullable byte[] pattern) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8).trim();
        Long deptId = null;
        if (!"null".equals(body) && !body.isEmpty()) {
            try {
                deptId = Long.parseLong(body);
            } catch (NumberFormatException e) {
                log.warn("CsColleaguesNotifyListener: invalid deptId body={}", body);
                return;
            }
        }
        log.debug("CsColleaguesNotifyListener: received deptId={}", deptId);
        colleagueNotifyService.pushColleagueListToLocalDeptUsers(deptId);
    }
}
