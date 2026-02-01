package com.skydawn.desk.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.util.ContentCachingRequestWrapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Web MVC 配置
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @NonNull
    private final ApiSignatureInterceptor apiSignatureInterceptor;
    @NonNull
    private final LoginInterceptor loginInterceptor;

    public WebMvcConfig(@NonNull ApiSignatureInterceptor apiSignatureInterceptor,
                        @NonNull LoginInterceptor loginInterceptor) {
        this.apiSignatureInterceptor = apiSignatureInterceptor;
        this.loginInterceptor = loginInterceptor;
    }

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {

        // 登录拦截器
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/csdesk/**", "/desk/**")
                .excludePathPatterns(
                        "/csdesk/scripts/**",    // JS 文件
                        "/csdesk/styles/**",     // CSS 文件
                        "/csdesk/media/**",     // 媒体文件
                        "/desk/captcha",        // 验证码（登录页用）
                        "/desk/sysUserLogin",   // 登录
                        "/desk/checkLogin",     // 检查登录状态
                        "/desk/logout"          // 登出（未登录调用无害）
                );

        // API 签名拦截器（注释掉可以不加签名测试）
        registry.addInterceptor(apiSignatureInterceptor)
                // 需要验证的路径
                .addPathPatterns("/message/sender/**")
                // 后续新增的开放接口可在此添加
                // .addPathPatterns("/api/**")

                // 排除的路径
                .excludePathPatterns(
                        "/message/callback/**",  // 外部系统回调（使用 Token 验证）
                        "/actuator/**",          // 健康检查
                        "/csdesk/**",            // 静态资源
                        "/error"                 // 错误页面
                );
    }

    /**
     * 请求体缓存过滤器
     * 允许多次读取请求体（签名验证和Controller都需要读取）
     */
    @Bean
    public OncePerRequestFilter contentCachingFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(@NonNull HttpServletRequest request,
                                            @NonNull HttpServletResponse response,
                                            @NonNull FilterChain filterChain)
                    throws ServletException, IOException {
                // 只对需要签名验证的路径包装请求
                if (shouldCache(request)) {
                    ContentCachingRequestWrapper wrappedRequest =
                            new ContentCachingRequestWrapper(request);
                    filterChain.doFilter(wrappedRequest, response);
                } else {
                    filterChain.doFilter(request, response);
                }
            }

            private boolean shouldCache(HttpServletRequest request) {
                String uri = request.getRequestURI();
                return uri.startsWith("/message/sender/");
            }
        };
    }
}
