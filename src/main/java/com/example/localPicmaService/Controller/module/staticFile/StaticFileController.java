package com.example.localPicmaService.Controller.module.staticFile;

import cn.hutool.jwt.JWT;
import com.example.localPicmaService.SecurityControl.JwtFilter;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Map;

@Controller
@RequestMapping("/static")
public class StaticFileController {


    @GetMapping("/logincss")
    public String login() {
        return "forward:/module/login/login.css";
    }

    @GetMapping("/homepagebasecss")
    public String homepagebasecss() {
        return "module/HomePage/base.css";
    }

    @GetMapping("/homepagelayoutcss")
    public String homepagelayoutcss() {
        return "module/HomePage/layout.css";
    }

    @GetMapping("/homepagecomponentscss")
    public String homepagecomponentscss() {
        return "module/HomePage/components.css";
    }

    @GetMapping("/homepageanimationscss")
    public String homepageanimationscss() {
        return "module/HomePage/animations.css";
    }

    @GetMapping("/homepageapijs")
    public String homepageapijs() {
        return "module/HomePage/api.js";
    }

    @GetMapping("/homepageappjs")
    public String homepageappcss() {
        return "module/HomePage/app.js";
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

    @GetMapping("/home")
    public String home() {
        return "forward:/module/Home/homePage.html";
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



}
