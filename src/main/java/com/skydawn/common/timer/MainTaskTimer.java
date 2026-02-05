package com.skydawn.common.timer;

import com.skydawn.common.Vars;
import com.skydawn.desk.core.service.SysGlobalPropertyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 主定时任务：定期检查 property-version 是否变更，变更则刷新全局属性缓存。
 */
@Component
public class MainTaskTimer {

    private static final Logger log = LoggerFactory.getLogger(MainTaskTimer.class);

    private static final String CACHE_KEY_PROPERTY_VERSION = "innersystem.version.property-version";

    @Autowired
    private SysGlobalPropertyService globalPropertyService;

    /**
     * 定时检查 property-version，若变更则刷新 Vars.SYS_GLOBAL_PROPERTY。
     * 首次延迟与间隔使用同一配置，默认 50 秒。
     */
    @Scheduled(fixedRateString = "${sys.timmer.main-task-interval:50000}",
               initialDelayString = "${sys.timmer.main-task-interval:50000}")
    public void refreshPropertyIfVersionChanged() {
        if (globalPropertyService == null) {
            log.warn("SysGlobalPropertyService 未注入，跳过版本检查");
            return;
        }
        try {
            Map<String, String> versions = globalPropertyService.getVersions();
            String newPropertyVersion = versions.get("propertyVersion");
            if (newPropertyVersion == null) {
                log.debug("未获取到 property-version，跳过刷新");
                return;
            }

            Map<String, String> cached = Vars.getSysGlobalProperty();
            String currentVersion = cached != null ? cached.get(CACHE_KEY_PROPERTY_VERSION) : null;
            if (newPropertyVersion.equals(currentVersion)) {
                return;
            }

            log.info("property-version 已变更: {} -> {}，刷新全局属性缓存", currentVersion, newPropertyVersion);
            Map<String, String> properties = globalPropertyService.getPropertiesMap();
            Vars.setSysGlobalProperty(properties);
            log.info("全局属性缓存已刷新，共 {} 条", properties.size());
        } catch (Exception e) {
            log.error("检查/刷新全局属性失败: {}", e.getMessage(), e);
        }
    }
}
