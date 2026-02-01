package com.skydawn.desk.core.controller;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 媒体代理：用 WABA 鉴权请求媒体 URL 并转发给前端，解决浏览器直接请求 401。
 * GET /desk/media/proxy?url=ENCODED_MEDIA_URL
 */
@RestController
@RequestMapping("/desk/media")
public class DeskMediaProxyController {

    private static final Logger log = LoggerFactory.getLogger(DeskMediaProxyController.class);

    /** 只允许代理这些主机（WABA 媒体 CDN），防止滥用 */
    private static final List<String> ALLOWED_HOSTS = List.of("facebook.com", "fbsbx.com", "lookaside.fbsbx.com");

    @Value("${waba.authorization:}")
    private String wabaAuthorization;

    @GetMapping("/proxy")
    public void proxy(
            @RequestParam("url") String url,
            jakarta.servlet.http.HttpServletRequest httpRequest,
            HttpServletResponse response) throws IOException {

        if (httpRequest.getSession().getAttribute(SysUserLoginController.SESSION_USER_KEY) == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Not logged in");
            return;
        }
        if (url == null || url.isBlank()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "url required");
            return;
        }
        String decodedUrl = java.net.URLDecoder.decode(url, StandardCharsets.UTF_8);
        if (!decodedUrl.startsWith("https://")) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "only https allowed");
            return;
        }
        URI uri;
        try {
            uri = URI.create(decodedUrl);
        } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "invalid url");
            return;
        }
        String host = uri.getHost();
        if (host == null || !ALLOWED_HOSTS.contains(host)) {
            log.warn("media proxy rejected host: {}", host);
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "host not allowed");
            return;
        }
        if (wabaAuthorization == null || wabaAuthorization.isBlank()) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "proxy not configured");
            return;
        }

        HttpGet httpGet = new HttpGet(uri);
        httpGet.setHeader(HttpHeaders.AUTHORIZATION, wabaAuthorization);
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            client.execute(httpGet, resp -> {
                int code = resp.getCode();
                if (code < 200 || code >= 300) {
                    response.sendError(HttpServletResponse.SC_BAD_GATEWAY, "upstream " + code);
                    return null;
                }
                var ctHeader = resp.getFirstHeader(HttpHeaders.CONTENT_TYPE);
                String contentType = ctHeader != null ? ctHeader.getValue() : null;
                if (contentType != null && !contentType.isBlank()) {
                    response.setContentType(contentType);
                } else {
                    response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
                }
                // 流式转发，避免大视频整段进内存导致卡死/OOM
                try (InputStream in = resp.getEntity().getContent()) {
                    OutputStream out = response.getOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        out.write(buf, 0, n);
                    }
                }
                return null;
            });
        } catch (Exception e) {
            boolean clientAbort = isClientAbort(e);
            if (clientAbort) {
                log.debug("media proxy client closed url={}", decodedUrl);
            } else {
                log.warn("media proxy failed url={}", decodedUrl, e);
            }
            if (!response.isCommitted()) {
                response.sendError(HttpServletResponse.SC_BAD_GATEWAY, "proxy failed");
            }
        }
    }

    private static boolean isClientAbort(Throwable t) {
        for (Throwable x = t; x != null; x = x.getCause()) {
            if (x instanceof java.io.IOException && x.getMessage() != null && x.getMessage().contains("Broken pipe")) {
                return true;
            }
            if (x.getClass().getSimpleName().contains("ClientAbort")) {
                return true;
            }
        }
        return false;
    }
}
