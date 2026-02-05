package com.skydawn.desk.core.service;

import com.skydawn.common.Defs;
import com.skydawn.common.crypto.DbValueCrypto;
import com.skydawn.desk.core.entity.SysGlobalProperty;
import com.skydawn.desk.core.mapper.SysGlobalPropertyMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 全局属性配置服务类
 */
@Service
public class SysGlobalPropertyService {
    private static final Logger log = LoggerFactory.getLogger(SysGlobalPropertyService.class);
    
    @Autowired
    private SysGlobalPropertyMapper globalPropertyMapper;
    
    @Value("${sys.pwd.cipherB64-decode-token}")
    private String cipherDecodeToken;
    
    /**
     * 获取所有全局属性配置列表（仅 belong_sys = Defs.SYS_CODE 或 Defs.ALL）
     * @return 全局属性配置列表
     */
    public List<SysGlobalProperty> getAllProperties() {
        try {
            List<SysGlobalProperty> properties = globalPropertyMapper.selectAllForApp(
                    Arrays.asList(Defs.SYS_CODE, Defs.ALL));
            return properties;
        } catch (org.springframework.dao.DataAccessException e) {
            // 数据库访问异常
            String errorDetail = String.format("数据库访问异常 - 异常类型: %s, 消息: %s", 
                    e.getClass().getSimpleName(), e.getMessage());
            log.error("获取全局属性配置列表失败（数据库异常）: {}", errorDetail, e);
            throw new RuntimeException("获取全局属性配置列表失败: " + errorDetail, e);
        } catch (Exception e) {
            // 其他异常
            String errorDetail = String.format("未知异常 - 异常类型: %s, 消息: %s", 
                    e.getClass().getName(), e.getMessage());
            log.error("获取全局属性配置列表失败（未知异常）: {}", errorDetail, e);
            throw new RuntimeException("获取全局属性配置列表失败: " + errorDetail, e);
        }
    }
    
    /**
     * 获取全局属性配置的 Map
     * 键格式：system_name.category.prop_key
     * 值：prop_value（如果 is_cipher=1 则解密后返回）
     * @return 全局属性配置 Map
     */
    public Map<String, String> getPropertiesMap() {
        Map<String, String> resultMap = new HashMap<>();
        
        try {
            List<SysGlobalProperty> properties = getAllProperties();
            
            if (properties == null || properties.isEmpty()) {
                log.warn("全局属性配置列表为空");
                return resultMap;
            }
            
            if (cipherDecodeToken == null || cipherDecodeToken.isBlank()) {
                log.warn("解密 Token 未配置，无法解密密文属性");
            }
            
            for (SysGlobalProperty property : properties) {
                if (property == null) {
                    continue;
                }
                
                // 构建键：system_name.category.prop_key
                String key = buildPropertyKey(property.getSystemName(), 
                                             property.getCategory(), 
                                             property.getPropKey());
                
                // 获取值
                String value = property.getPropValue();
                
                // 如果是密文，需要解密
                if (property.getIsCipher() != null && property.getIsCipher() == 1) {
                    if (value != null && !value.isBlank()) {
                        try {
                            if (cipherDecodeToken != null && !cipherDecodeToken.isBlank()) {
                                value = DbValueCrypto.decryptFromBase64(cipherDecodeToken, value);
                            } else {
                                log.warn("属性 {} 为密文，但解密 Token 未配置，使用原值", key);
                            }
                        } catch (IllegalArgumentException e) {
                            // 参数异常（解密参数错误）
                            log.error("属性 {} 解密失败（参数异常）: {}", key, e.getMessage(), e);
                            continue;
                        } catch (RuntimeException e) {
                            // 运行时异常（如加密算法异常）
                            String errorDetail = String.format("运行时异常 - 异常类型: %s, 消息: %s", 
                                    e.getClass().getSimpleName(), e.getMessage());
                            log.error("属性 {} 解密失败（运行时异常）: {}", key, errorDetail, e);
                            continue;
                        } catch (Exception e) {
                            // 其他异常
                            String errorDetail = String.format("未知异常 - 异常类型: %s, 消息: %s", 
                                    e.getClass().getName(), e.getMessage());
                            log.error("属性 {} 解密失败（未知异常）: {}", key, errorDetail, e);
                            continue;
                        }
                    }
                }
                
                resultMap.put(key, value);
            }
            
            return resultMap;
            
        } catch (org.springframework.dao.DataAccessException e) {
            // 数据库访问异常
            String errorDetail = String.format("数据库访问异常 - 异常类型: %s, 消息: %s", 
                    e.getClass().getSimpleName(), e.getMessage());
            log.error("构建全局属性配置 Map 失败（数据库异常）: {}", errorDetail, e);
            throw new RuntimeException("构建全局属性配置 Map 失败: " + errorDetail, e);
        } catch (Exception e) {
            // 其他异常
            String errorDetail = String.format("未知异常 - 异常类型: %s, 消息: %s", 
                    e.getClass().getName(), e.getMessage());
            log.error("构建全局属性配置 Map 失败（未知异常）: {}", errorDetail, e);
            throw new RuntimeException("构建全局属性配置 Map 失败: " + errorDetail, e);
        }
    }
    
    /**
     * 一次查询获取两个版本号（property-version 和 hub-version）
     * 注意：版本号不会加密，直接返回原值
     * @return 包含 propertyVersion 和 hubVersion 的 Map，key 为 "propertyVersion" 和 "hubVersion"
     */
    public Map<String, String> getVersions() {
        Map<String, String> versions = new HashMap<>();
        try {
            // 一次查询获取 innersystem.version 分类下的所有属性（仅本系统或 all）
            List<SysGlobalProperty> properties = globalPropertyMapper.selectBySystemAndCategoryForApp(
                    "innersystem", "version", Arrays.asList(Defs.SYS_CODE, Defs.ALL));
            
            if (properties == null || properties.isEmpty()) {
                log.warn("未找到版本配置属性");
                return versions;
            }
            
            // 遍历查找两个版本号（版本号不会加密，直接使用原值）
            for (SysGlobalProperty property : properties) {
                if (property == null) {
                    continue;
                }
                
                String propKey = property.getPropKey();
                String value = property.getPropValue();
                
                if ("property-version".equals(propKey) && value != null) {
                    versions.put("propertyVersion", value);
                } else if ("hub-version".equals(propKey) && value != null) {
                    versions.put("hubVersion", value);
                }
            }
            
            return versions;
            
        } catch (org.springframework.dao.DataAccessException e) {
            // 数据库访问异常
            String errorDetail = String.format("数据库访问异常 - 异常类型: %s, 消息: %s", 
                    e.getClass().getSimpleName(), e.getMessage());
            log.error("获取版本号失败（数据库异常）: {}", errorDetail, e);
            return versions;
        } catch (Exception e) {
            // 其他异常
            String errorDetail = String.format("未知异常 - 异常类型: %s, 消息: %s", 
                    e.getClass().getName(), e.getMessage());
            log.error("获取版本号失败（未知异常）: {}", errorDetail, e);
            return versions;
        }
    }
    
    /**
     * 构建属性键：system_name.category.prop_key
     * @param systemName 系统名称
     * @param category 分类
     * @param propKey 参数名
     * @return 属性键
     */
    private String buildPropertyKey(String systemName, String category, String propKey) {
        if (systemName == null) systemName = "";
        if (category == null) category = "";
        if (propKey == null) propKey = "";
        return systemName + "." + category + "." + propKey;
    }
}
