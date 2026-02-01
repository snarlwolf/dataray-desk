package com.skydawn.desk.core.config;

import com.skydawn.desk.core.controller.SysUserLoginController;
import com.skydawn.desk.core.entity.SysUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录拦截器
 * 拦截 /csdesk/cservice/** 路径，未登录则重定向到登录页
 */
@Component
public class LoginInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(LoginInterceptor.class);

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler)
            throws Exception {

        String uri = request.getRequestURI();
        if (uri == null) {
            return true;
        }
        HttpSession session = request.getSession(false);
        SysUser user = (session != null) ?
                (SysUser) session.getAttribute(SysUserLoginController.SESSION_USER_KEY) : null;

        // 访问 cservice/** 但未登录 -> 重定向到登录页
        if (uri.contains("/cservice/") && user == null) {
            log.debug("未登录访问受保护页面: {}", uri);
            response.sendRedirect("/csdesk/cserviceLogin.html");
            return false;
        }

        // 访问 /desk/** API 但未登录 -> 返回 401（登录相关路径已由 excludePathPatterns 排除）
        if (uri.startsWith("/desk/") && user == null) {
            log.debug("未登录访问受保护 API: {}", uri);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
            return false;
        }

        // 已登录访问登录页 -> 重定向到控制台
        if (uri.endsWith("/cserviceLogin.html") && user != null) {
            log.debug("已登录用户访问登录页，重定向到控制台");
            response.sendRedirect("/csdesk/cservice/console.html");
            return false;
        }

        return true;
    }
}
