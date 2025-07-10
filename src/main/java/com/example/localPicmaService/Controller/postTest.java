package com.example.localPicmaService.Controller;

import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/api")
public class postTest {
    // 示例 1: 接收 JSON 数据并返回响应
    @PostMapping("/users")
    public String createUser(@RequestBody(required = false) String str) throws IOException {
        return str;
    }

    // 修改点：GET 请求不支持 @RequestBody，改为使用 @RequestParam
    @GetMapping("/usersget")
    public String createUserGet(@RequestParam(defaultValue = "defaultUser", required = false) String param) throws IOException {
        return "Received param: " + param;
    }
}