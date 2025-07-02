package com.example.localPicmaService.Controller;


import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/api")
public class postTest {
    // 示例 1: 接收 JSON 数据并返回响应
    @PostMapping("/users")
    public String createUser(@RequestBody String str) throws IOException {
        return str;
    }
}
