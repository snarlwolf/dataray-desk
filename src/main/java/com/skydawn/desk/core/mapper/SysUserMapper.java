package com.skydawn.desk.core.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.skydawn.desk.core.entity.SysUser;

@Mapper
public interface SysUserMapper {

    // 新增用户
    int insert(SysUser user);

    // 根据 user_id 更新用户
    int updateById(SysUser user);

    // 根据 user_id 查询
    SysUser selectById(@Param("userId") Long userId);

    // 查询全部
    List<SysUser> selectAll();

    // 根据用户名模糊查询
    List<SysUser> selectByUserName(@Param("userName") String userName);

    // 根据部门查询
    List<SysUser> selectByDeptId(@Param("deptId") Long deptId);
    
	// 根据登录名精确查询（=）
    SysUser findByLoginName(@Param("loginName") String loginName);
}
