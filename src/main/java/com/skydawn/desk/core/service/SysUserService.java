package com.skydawn.desk.core.service;

import com.skydawn.common.crypto.BcCrypto;
import com.skydawn.desk.core.entity.SysUser;
import com.skydawn.desk.core.mapper.SysUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 用户服务
 */
@Service
public class SysUserService {

    private static final Logger log = LoggerFactory.getLogger(SysUserService.class);

    private final SysUserMapper sysUserMapper;

    public SysUserService(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    /**
     * 用户登录验证
     *
     * @param username 用户名
     * @param password 明文密码
     * @param loginIp  登录IP
     * @return 登录成功返回用户对象，失败返回 null
     */
    public SysUser login(String username, String password, String loginIp) {
        if (username == null || password == null) {
            log.warn("登录失败 - 用户名或密码为空");
            return null;
        }

        // 查询用户
        SysUser user = sysUserMapper.findByLoginName(username);
        if (user == null) {
            log.warn("登录失败 - 用户不存在: {}", username);
            return null;
        }

        // 检查用户状态
        if (!"0".equals(user.getStatus())) {
            log.warn("登录失败 - 用户已禁用: {}", username);
            return null;
        }

        // 验证密码（BcCrypto：明文、密文）
        if (!BcCrypto.matchesPassword(password, user.getPassword())) {
            log.warn("登录失败 - 密码错误: {}", username);
            return null;
        }

        // 更新登录信息
        user.setLoginIp(loginIp);
        user.setLoginDate(LocalDateTime.now());
        sysUserMapper.updateById(user);

        log.info("登录成功 - 用户: {}, IP: {}", username, loginIp);

        // 清除密码后返回
        user.setPassword(null);
        return user;
    }

    /**
     * 根据用户ID查询用户
     */
    public SysUser findById(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user != null) {
            user.setPassword(null);
        }
        return user;
    }
}
