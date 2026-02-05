package com.skydawn.desk.core.mapper;

import com.skydawn.desk.core.entity.SysGlobalProperty;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SysGlobalPropertyMapper {
    
    /**
     * 查询所有全局属性配置（仅限 belong_sys 属于指定列表的配置）
     * @param belongSysList 允许的 belong_sys 值，如 Defs.SYS_CODE、Defs.ALL
     * @return 全局属性配置列表
     */
    List<SysGlobalProperty> selectAllForApp(@Param("belongSysList") List<String> belongSysList);
    
    /**
     * 查询所有全局属性配置（不过滤 belong_sys，保留用于兼容）
     * @return 全局属性配置列表
     */
    List<SysGlobalProperty> selectAll();
    
    /**
     * 根据条件查询全局属性配置
     * @param systemName 系统名称
     * @param category 分类
     * @param propKey 参数名
     * @return 全局属性配置，如果不存在返回 null
     */
    SysGlobalProperty selectByKey(
            @Param("systemName") String systemName,
            @Param("category") String category,
            @Param("propKey") String propKey);
    
    /**
     * 根据系统名称和分类查询全局属性配置列表
     * @param systemName 系统名称
     * @param category 分类
     * @return 全局属性配置列表
     */
    List<SysGlobalProperty> selectBySystemAndCategory(
            @Param("systemName") String systemName,
            @Param("category") String category);
    
    /**
     * 根据系统名称和分类查询全局属性配置列表（仅限 belong_sys 属于指定列表）
     * 用于版本号等仅取本系统配置的场景
     * @param systemName 系统名称
     * @param category 分类
     * @param belongSysList 允许的 belong_sys 值
     * @return 全局属性配置列表
     */
    List<SysGlobalProperty> selectBySystemAndCategoryForApp(
            @Param("systemName") String systemName,
            @Param("category") String category,
            @Param("belongSysList") List<String> belongSysList);
    
    /**
     * 更新版本号（仅更新 belong_sys 属于指定列表的配置行）
     * @param systemName 系统名称
     * @param category 分类
     * @param propKey 参数名
     * @param propValue 新的版本号值
     * @param updatedBy 更新人
     * @param belongSysList 允许的 belong_sys 值，避免误更新其他系统配置
     * @return 更新的行数
     */
    int updateVersion(
            @Param("systemName") String systemName,
            @Param("category") String category,
            @Param("propKey") String propKey,
            @Param("propValue") String propValue,
            @Param("updatedBy") String updatedBy,
            @Param("belongSysList") List<String> belongSysList);
}
