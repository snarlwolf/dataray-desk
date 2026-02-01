package com.skydawn.desk.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/**
 * Spring Security 配置：仅启用 CSRF 保护与会话安全，认证由 LoginInterceptor 负责。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String PATH_CALLBACK = "/message/callback/**";
    private static final String PATH_SENDER = "/message/sender/**";
    private static final String PATH_ACTUATOR = "/actuator/**";
    /** 客服台 API：不校验 CSRF。控制台为同源 SPA，首次加载时可能未下发 CSRF cookie，导致发消息等 POST 被 403；仍依赖 Session + SameSite 防跨站。 */
    private static final String PATH_DESK = "/desk/**";

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .ignoringRequestMatchers(PATH_CALLBACK, PATH_SENDER, PATH_ACTUATOR, PATH_DESK))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);

        return http.build();
    }
}
