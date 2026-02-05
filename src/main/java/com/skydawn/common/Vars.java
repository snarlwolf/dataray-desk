package com.skydawn.common;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

/**
 * Global runtime variables holder.
 * - No DB logic here.
 * - Other components decide when/how to load/refresh/clear.
 */
@Component
public final class Vars implements ApplicationContextAware {
    private Vars() {}
    
    private static final Logger log = LoggerFactory.getLogger(Vars.class);
    
    private static ApplicationContext applicationContext;

    /**
     * sys_message_hub cached data.
     * Rule: if null -> not loaded; if non-null -> use it.
     */
    private static final AtomicReference<Map<String, String>> SYS_GLOBAL_PROPERTY = new AtomicReference<>();
    
    // 用于双重检查锁定的锁对象
    private static final Object SYS_GLOBAL_PROPERTY_LOCK = new Object();


    /**
     * Get cached global properties.
     * If cache is empty, automatically load from database via SysGlobalPropertyService.getPropertiesMap().
     * Uses double-check locking to prevent race conditions.
     * @return Global properties Map, never null (returns empty map if load fails)
     */
    public static Map<String, String> getSysGlobalProperty() {
        // 第一次检查（无锁，快速路径）
        Map<String, String> cached = SYS_GLOBAL_PROPERTY.get();
        if (cached != null) {
            return cached;
        }
        
        // 双重检查锁定：同步块内再次检查
        synchronized (SYS_GLOBAL_PROPERTY_LOCK) {
            // 第二次检查（有锁，防止其他线程已经加载）
            cached = SYS_GLOBAL_PROPERTY.get();
            if (cached != null) {
                return cached;
            }
            
            // 缓存确实为空，通过 Service 加载
            try {
                if (applicationContext != null) {
                    com.skydawn.desk.core.service.SysGlobalPropertyService service = 
                        applicationContext.getBean(com.skydawn.desk.core.service.SysGlobalPropertyService.class);
                    if (service != null) {
                        Map<String, String> properties = service.getPropertiesMap();
                        // 设置到缓存
                        setSysGlobalProperty(properties);
                        return properties;
                    }
                }
            } catch (Exception e) {
                // 如果获取 Service 失败，记录日志但不抛出异常，返回空 Map
                log.error("Failed to load global properties from service", e);
            }
            
            // 如果加载失败，返回空 Map（避免返回 null）
            return Map.of();
        }
    }

    /**
     * Set cached global properties.
     * Use immutable copy to prevent accidental modification by callers.
     */
    public static void setSysGlobalProperty(Map<String, String> properties) {
        SYS_GLOBAL_PROPERTY.set(properties == null ? null : Map.copyOf(properties));
    }

    /** Clear cached global properties (so next access can trigger reload by other module). */
    public static void clearSysGlobalProperty() {
        SYS_GLOBAL_PROPERTY.set(null);
    }

    /** Clear all runtime vars. */
    public static void clearAll() {
        clearSysGlobalProperty();
        // 未来新增变量，在这里统一清
    }
    
    @Override
    public void setApplicationContext(@NonNull ApplicationContext context) throws BeansException {
        applicationContext = context;
    }
}
