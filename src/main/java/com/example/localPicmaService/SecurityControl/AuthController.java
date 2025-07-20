package com.example.localPicmaService.SecurityControl;

import cn.hutool.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @PostMapping("/apilogin")
    public JSONObject login(@RequestBody JSONObject userInfo) {

        JSONObject res = new JSONObject();
        try {
            // 构建认证 Token（用户名 + 密码）
            UsernamePasswordAuthenticationToken token =
                    new UsernamePasswordAuthenticationToken(
                            userInfo.get("name"),
                            userInfo.get("password")
                    );

            Authentication authentication = authenticationManager.authenticate(token);

            String username = authentication.getName();
            Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();

            // 构造 payload
            Map<String, Object> payload = new HashMap<>();
            payload.put("username", username);
            payload.put("roles", authorities.stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toList()));


            // 生成 JWT（密钥建议保存在配置文件中）
            String jwt = JwtFilter.createToken(payload);

            res.put("username", username);
            res.put("token", jwt);
        } catch (Exception ex) {
            res.put("error", "用户名或密码错误");
        }
        return res;
    }
}