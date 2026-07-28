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
}
