package com.example.localPicmaService.SecurityControl;

import cn.hutool.json.JSONObject;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseCookie;
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
    public JSONObject login(@RequestBody JSONObject userInfo,
                            HttpServletResponse response) {   // ← 新增参数

        JSONObject res = new JSONObject();
        try {
            UsernamePasswordAuthenticationToken token =
                    new UsernamePasswordAuthenticationToken(
                            userInfo.get("name"),
                            userInfo.get("password")
                    );

            Authentication authentication = authenticationManager.authenticate(token);

            String username = authentication.getName();
            Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();

            Map<String, Object> payload = new HashMap<>();
            payload.put("username", username);
            payload.put("roles", authorities.stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toList()));

            String jwt = JwtFilter.createToken(payload);

            // ★ 唯一新增：写 Cookie
            ResponseCookie cookie = ResponseCookie.from("AUTH_TOKEN", jwt)
                    .httpOnly(true)
                    .secure(false)       // 生产环境改 true
                    .path("/")
                    .maxAge(86400)
                    .sameSite("Lax")
                    .build();
            response.addHeader("Set-Cookie", cookie.toString());

            // 以下不变
            res.put("username", username);
            res.put("token", jwt);
        //    res.put("roles", roles);   // ← 建议加一行，把角色也返回给前端

        } catch (Exception ex) {
            ex.printStackTrace();
            res.put("error", "用户名或密码错误");
        }
        return res;
    }

}