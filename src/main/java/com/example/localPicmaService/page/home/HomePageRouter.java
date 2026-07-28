package com.example.localPicmaService.page.home;

import com.example.localPicmaService.page.ResourceController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomePageRouter {

    @GetMapping("/")
    public ResponseEntity<byte[]> index() {
        return ResourceController.serveFile("static/module/HomePage/homePage.html");
    }

    @GetMapping("/home")
    public ResponseEntity<byte[]> home() {
        return ResourceController.serveFile("static/module/HomePage/homePage.html");
    }
}
