package com.example.localPicmaService.Controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 页面路由控制器
 * <p>
 * 负责页面级路由，通过 ResourceController.serveFile() 从 classpath 读取 HTML 文件。
 * <p>
 * 路由说明：
 *   GET /       → 首页（公开访问，无需登录）
 *   GET /login  → 登录页
 *   GET /home   → 首页（公开访问，无需登录）
 */
@RestController
public class PageController {

    /**
     * 登录页路由
     */
    @GetMapping("/login")
    public ResponseEntity<byte[]> login() {
        return ResourceController.serveFile("static/module/login/login.html");
    }

    /**
     * 根路径路由：直接展示首页
     * <p>
     * 首页为公开页面，无需登录即可访问。
     * 登录状态由前端 JS 自行判断，后端不做强制跳转。
     */
    @GetMapping("/")
    public ResponseEntity<byte[]> index() {
        return ResourceController.serveFile("static/module/HomePage/homePage.html");
    }

    /**
     * 首页路由
     */
    @GetMapping("/home")
    public ResponseEntity<byte[]> home() {
        return ResourceController.serveFile("static/module/HomePage/homePage.html");
    }
}
