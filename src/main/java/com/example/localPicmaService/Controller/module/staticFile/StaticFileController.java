package com.example.localPicmaService.Controller.module.staticFile;

import cn.hutool.jwt.JWT;
import com.example.localPicmaService.SecurityControl.JwtFilter;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Controller
@RequestMapping("/static")
public class StaticFileController {


    @GetMapping("/logincss")
    public void login(HttpServletResponse response) throws IOException {
        sendFile("/module/login/login.css", response);
    }

    @GetMapping("/homepagebasecss")
    public void homepagebasecss(HttpServletResponse response) throws IOException {
        sendFile("/module/HomePage/base.css", response);
    }

    @GetMapping("/homepagelayoutcss")
    public void homepagelayoutcss(HttpServletResponse response) throws IOException {
        sendFile("/module/HomePage/layout.css", response);
    }

    @GetMapping("/homepagecomponentscss")
    public void homepagecomponentscss(HttpServletResponse response) throws IOException {
        sendFile("/module/HomePage/components.css", response);
    }


    @GetMapping("/homepageapijs")
    public void homepageapijs(HttpServletResponse response) throws IOException {
        sendFile("/module/HomePage/api.js", response);
    }

    @GetMapping("/homepageappjs")
    public void homepageappjs(HttpServletResponse response) throws IOException {
        sendFile("/module/HomePage/app.js", response);
    }

    @GetMapping("/")
    public String index(HttpServletRequest request) {
        // 从 Cookie 或 Header 中取 token 验证
        String token = extractToken(request);
        if (token != null) {
            try {
                JWT jwt = JwtFilter.parseAndVerify(token);
                if (jwt.verify()) {
                    return "forward:/module/HomePage/homePage.html";  // 已登录 → 首页
                }
            } catch (Exception ignored) {
            }
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

    private void sendFile(String path, HttpServletResponse response) throws IOException {
        Resource resource = new ClassPathResource("static" + path);
        if (!resource.exists()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String contentType = Files.probeContentType(Path.of(resource.getURI()));
        response.setContentType(contentType != null ? contentType : "application/octet-stream");
        try (InputStream in = resource.getInputStream()) {
            in.transferTo(response.getOutputStream());
        }
    }


}
