package com.example.localPicmaService.Controller;

import cn.hutool.core.io.resource.ClassPathResource;
import cn.hutool.core.io.resource.Resource;
import cn.hutool.jwt.JWT;
import com.example.localPicmaService.SecurityControl.JwtFilter;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Controller
public class PageController {

    @GetMapping("/login")
    public String login() {
        return "forward:/module/login/login.html";
    }

    @GetMapping("/publicAuth")
    public String publicAuth() {
        return "forward:/module/auth.js";
    }
    @GetMapping("/")
    public String index(HttpServletRequest request) {
        // 从 Cookie 或 Header 中取 token 验证
        String token = extractToken(request);
        if (token != null) {
            try {
                JWT jwt = JwtFilter.parseAndVerify(token);
                if (jwt.verify()) {
                    return "forward:/module/Home/homePage.html";  // 已登录 → 首页
                }
            } catch (Exception ignored) {}
        }
        return "redirect:/login";              // 未登录 → 登录页
    }

    // 复用 JwtFilter 的读取逻辑
    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("AUTH_TOKEN".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }


//    private void sendFile(String path, HttpServletResponse response) throws IOException {
//        Resource resource = new ClassPathResource("static" + path);
//        if (!resource.exists()) {
//            response.sendError(HttpServletResponse.SC_NOT_FOUND);
//            return;
//        }
//
//        // 自动判断 Content-Type
//        String contentType = Files.probeContentType(
//                Path.of(resource.getURI())
//        );
//        response.setContentType(contentType != null ? contentType : "application/octet-stream");
//
//        // 直接把文件内容写到响应里
//        try (InputStream in = resource.getInputStream()) {
//            in.transferTo(response.getOutputStream());
//        }
//    }
}
