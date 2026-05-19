package com.example.localPicmaService.SecurityControl;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class AuthCheckController {

    @GetMapping("/apicheck-token")
    public ResponseEntity<?> checkToken(HttpServletRequest request) {
        // JwtFilter 已经把认证信息放进了 request attribute
        // 如果到这里说明 token 有效
        String username = (String) request.getAttribute("authenticatedUser");
        if (username != null) {
            return ResponseEntity.ok(Map.of(
                    "valid", true,
                    "username", username
            ));
        }
        return ResponseEntity.status(401).body(Map.of(
                "valid", false
        ));
    }
}
