package com.skydawn.desk.sockets;

import java.security.Principal;
import java.util.Map;
import java.util.Objects;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.lang.NonNull;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import com.skydawn.desk.core.controller.SysUserLoginController;
import com.skydawn.desk.core.entity.SysUser;

import jakarta.servlet.http.HttpSession;

@Configuration
@EnableWebSocket
public class CsWebSocketConfig implements WebSocketConfigurer {

    private final CsWebSocketHandler csWebSocketHandler;

    public CsWebSocketConfig(CsWebSocketHandler csWebSocketHandler) {
        this.csWebSocketHandler = csWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(@NonNull WebSocketHandlerRegistry registry) {
        registry.addHandler(Objects.requireNonNull(csWebSocketHandler), "/ws/cs")
                .setHandshakeHandler(new DefaultHandshakeHandler() {
                    @Override
                    protected Principal determineUser(@NonNull ServerHttpRequest request,
                                                      @NonNull WebSocketHandler handler,
                                                      @NonNull Map<String, Object> attributes) {
                        if (request instanceof ServletServerHttpRequest servlet) {
                            HttpSession session = servlet.getServletRequest().getSession(false);
                            if (session != null) {
                                Object user = session.getAttribute(SysUserLoginController.SESSION_USER_KEY);
                                if (user instanceof SysUser sysUser) {
                                    attributes.put(SysUserLoginController.SESSION_USER_KEY, sysUser);
                                    return () -> String.valueOf(sysUser.getUserId());
                                }
                            }
                        }
                        return null;
                    }
                })
                .setAllowedOrigins("*");
    }
}
