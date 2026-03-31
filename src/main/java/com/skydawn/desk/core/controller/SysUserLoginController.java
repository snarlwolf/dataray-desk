package com.skydawn.desk.core.controller;

import com.skydawn.desk.config.DeskPresenceProperties;
import com.skydawn.desk.core.entity.SysUser;
import com.skydawn.desk.core.service.SysUserService;
import com.skydawn.desk.service.CsLoginRedisService;
import com.skydawn.desk.sockets.CsMessageSendSockets;
import com.skydawn.redis.CsRedisKeys;
import com.skydawn.redis.RedisFinder;
import com.skydawn.redis.RedisOperation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 用户登录控制器
 */
@RestController
@RequestMapping("/desk")
public class SysUserLoginController {

    private static final Logger log = LoggerFactory.getLogger(SysUserLoginController.class);

    public static final String SESSION_USER_KEY = "loginUser";
    public static final String SESSION_CAPTCHA_KEY = "captchaCode";

    private final SysUserService sysUserService;
    private static final String SESSION_DESK_PRESENCE_RENEW_AT = "deskPresenceRenewAt";

    private final CsLoginRedisService csLoginRedisService;
    private final RedisFinder redisFinder;
    private final RedisOperation redisOperation;
    private final CsMessageSendSockets messageSendSockets;
    private final DeskPresenceProperties deskPresenceProperties;

    public SysUserLoginController(SysUserService sysUserService, CsLoginRedisService csLoginRedisService,
                                  RedisFinder redisFinder, RedisOperation redisOperation,
                                  CsMessageSendSockets messageSendSockets,
                                  DeskPresenceProperties deskPresenceProperties) {
        this.sysUserService = sysUserService;
        this.csLoginRedisService = csLoginRedisService;
        this.redisFinder = redisFinder;
        this.redisOperation = redisOperation;
        this.messageSendSockets = messageSendSockets;
        this.deskPresenceProperties = deskPresenceProperties;
    }

    /**
     * 用户登录。
     * 若本账号已在别处登录且未传 kickOtherSession，返回 alreadyLoggedInElsewhere 供前端弹窗确认；
     * 前端确认后带 kickOtherSession=true 再请求，服务端先广播挤下线再完成登录。
     */
    @PostMapping("/sysUserLogin")
    public ResponseEntity<Map<String, Object>> login(
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam String captcha,
            @RequestParam(required = false, defaultValue = "false") Boolean kickOtherSession,
            HttpServletRequest request) {

        Map<String, Object> result = new HashMap<>();
        HttpSession session = request.getSession();

        // 验证码校验
        String sessionCaptcha = (String) session.getAttribute(SESSION_CAPTCHA_KEY);
        if (sessionCaptcha == null || !sessionCaptcha.equalsIgnoreCase(captcha)) {
            result.put("success", false);
            result.put("message", "Invalid captcha");
            return ResponseEntity.ok(result);
        }

        String loginIp = getClientIp(request);
        SysUser user = sysUserService.login(username, password, loginIp);
        if (user == null) {
            result.put("success", false);
            result.put("message", "Invalid username or password");
            return ResponseEntity.ok(result);
        }

        // 先同步判断是否已在别处登录，未确认挤下线前绝不写 session、不返回 success（Redis/WebSocket 用登录名标识客服）
        String loginName = user.getUserName();
        boolean alreadyOnline = redisFinder.isUserOnline(loginName) || messageSendSockets.isOnline(loginName);
        if (alreadyOnline && !Boolean.TRUE.equals(kickOtherSession)) {
            result.put("success", false);
            result.put("alreadyLoggedInElsewhere", true);
            result.put("message", "This account is already logged in elsewhere.");
            return ResponseEntity.ok(result);
        }

        session.removeAttribute(SESSION_CAPTCHA_KEY);

        if (Boolean.TRUE.equals(kickOtherSession)) {
            redisOperation.publish(CsRedisKeys.CHANNEL_WS_CLOSE_ELSEWHERE, loginName + ":");
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        session.setAttribute(SESSION_USER_KEY, user);
        csLoginRedisService.onLoginSuccess(loginName);

        result.put("success", true);
        result.put("message", "Login successful");
        result.put("redirectUrl", "/csdesk/cservice/console.html");

        return ResponseEntity.ok(result);
    }

    /**
     * 获取验证码图片
     */
    @GetMapping("/captcha")
    public ResponseEntity<Map<String, String>> getCaptcha(HttpServletRequest request) {
        // 生成随机验证码
        String captchaCode = generateCaptchaCode(4);
        request.getSession().setAttribute(SESSION_CAPTCHA_KEY, captchaCode);

        // 生成图片
        String base64Image = generateCaptchaImage(captchaCode);

        Map<String, String> result = new HashMap<>();
        result.put("image", "data:image/png;base64," + base64Image);

        return ResponseEntity.ok(result);
    }

    /**
     * 获取当前登录用户信息
     */
    @GetMapping("/currentUser")
    public ResponseEntity<Map<String, Object>> getCurrentUser(HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        HttpSession session = request.getSession();
        SysUser user = (SysUser) session.getAttribute(SESSION_USER_KEY);

        if (user == null) {
            result.put("success", false);
            result.put("message", "Not logged in");
            return ResponseEntity.ok(result);
        }

        long now = System.currentTimeMillis();
        Long lastRenew = (Long) session.getAttribute(SESSION_DESK_PRESENCE_RENEW_AT);
        long minIv = deskPresenceProperties.getHttpRenewMinIntervalMs();
        if (lastRenew == null || now - lastRenew >= minIv) {
            csLoginRedisService.renewOnlinePresence(user.getUserName());
            session.setAttribute(SESSION_DESK_PRESENCE_RENEW_AT, now);
        }

        result.put("success", true);
        result.put("userId", user.getUserId());
        result.put("username", user.getUserName());
        result.put("nickname", user.getNickName());

        return ResponseEntity.ok(result);
    }

    /**
     * 退出登录
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpServletRequest request) {
        SysUser user = (SysUser) request.getSession().getAttribute(SESSION_USER_KEY);
        if (user != null) {
            csLoginRedisService.onLogout(user.getUserName());
        }
        request.getSession().invalidate();

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Logout successful");
        result.put("redirectUrl", "/csdesk/cserviceLogin.html");

        return ResponseEntity.ok(result);
    }

    /**
     * 检查登录状态
     */
    @GetMapping("/checkLogin")
    public ResponseEntity<Map<String, Object>> checkLogin(HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        SysUser user = (SysUser) request.getSession().getAttribute(SESSION_USER_KEY);

        result.put("loggedIn", user != null);
        if (user != null) {
            result.put("redirectUrl", "/csdesk/cservice/console.html");
        }

        return ResponseEntity.ok(result);
    }

    /**
     * 生成随机验证码
     */
    private String generateCaptchaCode(int length) {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    /**
     * 生成验证码图片
     */
    private String generateCaptchaImage(String code) {
        int width = 120;
        int height = 40;

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        // 背景
        g.setColor(new Color(240, 248, 255));  // 淡蓝色背景
        g.fillRect(0, 0, width, height);

        // 干扰线
        Random random = new Random();
        g.setColor(new Color(180, 200, 220));
        for (int i = 0; i < 6; i++) {
            int x1 = random.nextInt(width);
            int y1 = random.nextInt(height);
            int x2 = random.nextInt(width);
            int y2 = random.nextInt(height);
            g.drawLine(x1, y1, x2, y2);
        }

        // 验证码文字
        g.setFont(new Font("Arial", Font.BOLD, 28));
        for (int i = 0; i < code.length(); i++) {
            g.setColor(new Color(50 + random.nextInt(100), 50 + random.nextInt(100), 150 + random.nextInt(50)));
            g.drawString(String.valueOf(code.charAt(i)), 20 + i * 25, 30);
        }

        g.dispose();

        // 转 Base64
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "PNG", baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            log.error("生成验证码图片失败", e);
            return "";
        }
    }

    /**
     * 获取客户端 IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多个代理时取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
