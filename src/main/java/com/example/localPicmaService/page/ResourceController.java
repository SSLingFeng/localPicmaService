package com.example.localPicmaService.page;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 统一静态资源控制器
 * <p>
 * /public/res/**  — 公开资源，免登录
 * /auth/res/**    — 受保护资源，需 JWT 认证
 */
@RestController
public class ResourceController {

    @GetMapping("/public/res/**")
    public ResponseEntity<byte[]> publicResource(HttpServletRequest request) {
        String subPath = extractSubPath(request, "/public/res/");
        return serveFile("static/" + subPath);
    }

    @GetMapping("/auth/res/**")
    public ResponseEntity<byte[]> protectedResource(HttpServletRequest request) {
        String subPath = extractSubPath(request, "/auth/res/");
        return serveFile("static/" + subPath);
    }

    public static String extractSubPath(HttpServletRequest request, String prefix) {
        String uri = request.getRequestURI();
        return uri.substring(uri.indexOf(prefix) + prefix.length());
    }

    public static ResponseEntity<byte[]> serveFile(String classPathPath) {
        try {
            Resource resource = new ClassPathResource(classPathPath);
            if (!resource.exists()) {
                return ResponseEntity.notFound().build();
            }
            byte[] data;
            try (InputStream in = resource.getInputStream()) {
                data = in.readAllBytes();
            }
            String contentType;
            try {
                contentType = Files.probeContentType(Path.of(resource.getURI()));
            } catch (Exception e) {
                contentType = null;
            }
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(
                            contentType != null ? contentType : "application/octet-stream"))
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(data.length))
                    .body(data);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
