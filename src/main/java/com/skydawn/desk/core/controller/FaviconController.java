package com.skydawn.desk.core.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.util.Objects;

/**
 * 根路径的 favicon / apple-touch-icon 由浏览器自动请求。
 * 因 static-path-pattern 仅为 /csdesk/**，这些请求会 404，故由此控制器统一返回 favicon。
 */
@RestController
public class FaviconController {

    private static final Resource FAVICON = new ClassPathResource("static/favicon.ico");

    @GetMapping(value = { "/favicon.ico", "/apple-touch-icon.png", "/apple-touch-icon-precomposed.png" })
    @SuppressWarnings("null")
    public ResponseEntity<Resource> favicon(HttpServletRequest request) throws IOException {
        if (!FAVICON.exists() || !FAVICON.isReadable()) {
            return ResponseEntity.notFound().build();
        }
        String path = request.getRequestURI();
        if (path != null && path.endsWith(".png")) {
            return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(FAVICON);
        }
        return ResponseEntity.ok()
                .contentType(Objects.requireNonNull(MediaType.parseMediaType("image/x-icon")))
                .body(FAVICON);
    }
}
