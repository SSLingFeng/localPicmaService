package com.example.localPicmaService.page.login;

import com.example.localPicmaService.page.ResourceController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LoginRouter {

    @GetMapping("/login")
    public ResponseEntity<byte[]> login() {
        return ResourceController.serveFile("static/module/login/login.html");
    }

    @GetMapping("/register")
    public ResponseEntity<byte[]> register() {
        return ResourceController.serveFile("static/module/register/register.html");
    }

    @GetMapping("/admin")
    public ResponseEntity<byte[]> admin() {
        return ResourceController.serveFile("static/module/admin/admin.html");
    }

    @GetMapping("/squad")
    public ResponseEntity<byte[]> squad() {
        return ResourceController.serveFile("static/module/squad/squad.html");
    }

    @GetMapping("/cartoon")
    public ResponseEntity<byte[]> cartoon() {
        return ResourceController.serveFile("static/module/router/private/cartoon/main.html");
    }

    @GetMapping("/test")
    public ResponseEntity<byte[]> test() {
        return ResourceController.serveFile("static/module/test/main.html");
    }
}
