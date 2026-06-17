package com.example.localPicmaService.Controller;

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
 * 将 classpath:/static/ 下的前端资源通过接口对外暴露，
 * 而非直接放开 /static/** 路径，避免静态文件路径被随意访问。
 * <p>
 * 两套接口：
 *   /public/res/**  — 公开资源，免登录（登录页、首页等）
 *   /auth/res/**    — 受保护资源，需要 JWT 认证（后台管理等）
 * <p>
 * 路径映射规则（支持任意层级嵌套）：
 *   /public/res/module/HomePage/base.css                  →  classpath:static/module/HomePage/base.css
 *   /public/res/module/login/login.css                    →  classpath:static/module/login/login.css
 *   /auth/res/module/router/private/cartoon/style.css     →  classpath:static/module/router/private/cartoon/style.css
 */
@RestController
public class ResourceController {

    /**
     * 公开静态资源接口（免登录）
     * <p>
     * SecurityConfig 中已配置 /public/res/** 为 permitAll。
     * 使用 ** 通配符匹配任意层级路径，通过 HttpServletRequest 截取完整子路径。
     */
    @GetMapping("/public/res/**")
    public ResponseEntity<byte[]> publicResource(HttpServletRequest request) {
        String subPath = extractSubPath(request, "/public/res/");
        return serveFile("static/" + subPath);
    }

    /**
     * 受保护静态资源接口（需登录）
     * <p>
     * 未登录时请求会被 JwtFilter + SecurityConfig 拦截，返回 403。
     * 使用 ** 通配符匹配任意层级路径，通过 HttpServletRequest 截取完整子路径。
     */
    @GetMapping("/auth/res/**")
    public ResponseEntity<byte[]> protectedResource(HttpServletRequest request) {
        String subPath = extractSubPath(request, "/auth/res/");
        return serveFile("static/" + subPath);
    }

    // ==================== 公共工具方法 ====================

    /**
     * 从请求 URI 中截取前缀之后的子路径
     * <p>
     * 示例：请求 URI = /public/res/module/HomePage/base.css
     *       prefix = /public/res/
     *       返回值 = module/HomePage/base.css
     */
    public static String extractSubPath(HttpServletRequest request, String prefix) {
        String uri = request.getRequestURI();
        return uri.substring(uri.indexOf(prefix) + prefix.length());
    }

    /**
     * 从 classpath 读取文件并构建 ResponseEntity（供其他 Controller 复用）
     *
     * @param classPathPath classpath 下的相对路径（如 static/module/HomePage/base.css）
     * @return 文件字节流 + Content-Type，文件不存在返回 404
     */
    public static ResponseEntity<byte[]> serveFile(String classPathPath) {
        try {
            Resource resource = new ClassPathResource(classPathPath);
            if (!resource.exists()) {
                return ResponseEntity.notFound().build();
            }

            // 读取文件字节
            byte[] data;
            try (InputStream in = resource.getInputStream()) {
                data = in.readAllBytes();
            }

            // 推断 MIME 类型
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
