package com.example.localPicmaService.SecurityControl;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class AuthCheckController {

    @GetMapping("/apicheck-token")
    public ResponseEntity<?> checkToken(HttpServletRequest request) {
        // ★ 从 SecurityContext 读取（JwtFilter 验证成功后写入的）
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.isAuthenticated()
                && !"anonymousUser".equals(auth.getPrincipal())) {
            return ResponseEntity.ok(Map.of(
                    "valid", true,
                    "username", auth.getName()
            ));
        }

        return ResponseEntity.status(401).body(Map.of(
                "valid", false
        ));
    }
}
