package com.example.localPicmaService.page.auth.controller;

import cn.hutool.json.JSONObject;
import com.example.localPicmaService.security.JwtFilter;
import com.example.localPicmaService.tool.SQLTool.SqlUtil;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

@RestController
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @PostMapping("/page/login/api/login")
    public JSONObject login(@RequestBody JSONObject userInfo,
                            HttpServletResponse response) {
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

            ResponseCookie cookie = ResponseCookie.from("AUTH_TOKEN", jwt)
                    .httpOnly(true)
                    .secure(false)
                    .path("/")
                    .maxAge(86400)
                    .sameSite("Lax")
                    .build();
            response.addHeader("Set-Cookie", cookie.toString());

            res.put("username", username);
            res.put("token", jwt);
        } catch (Exception ex) {
            ex.printStackTrace();
            res.put("error", "用户名或密码错误");
        }
        return res;
    }

    @PostMapping("/page/login/api/register")
    public JSONObject register(@RequestBody JSONObject userInfo) {
        JSONObject res = new JSONObject();
        String username = userInfo.getStr("name");
        String password = userInfo.getStr("password");

        if (username == null || username.trim().isEmpty()) {
            res.put("error", "用户名不能为空");
            return res;
        }
        if (password == null || password.length() < 6) {
            res.put("error", "密码长度不能少于6位");
            return res;
        }

        try {
            List<Map<String, Object>> existing = SqlUtil.query(
                    "SELECT user_name FROM web_user WHERE user_name = {?varchar|reg_user?}",
                    Map.of("reg_user", username.trim()), 1);
            if (!existing.isEmpty()) {
                res.put("error", "用户名已存在");
                return res;
            }

            BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
            String encodedPassword = encoder.encode(password);
            String id = UUID.randomUUID().toString().replace("-", "");
            Date now = new Date();

            Map<String, Object> userData = new LinkedHashMap<>();
            userData.put("id", id);
            userData.put("ver", 1);
            userData.put("create_date", now);
            userData.put("update_date", now);
            userData.put("del_flag", 0);
            userData.put("user_name", username.trim());
            userData.put("password", encodedPassword);
            userData.put("displayname", username.trim());
            userData.put("enabled", 1);
            userData.put("role", "USER");

            SqlUtil.sync("web_user").insert(List.of(userData)).commit();

            List<Map<String, Object>> defaultRole = SqlUtil.query(
                    "SELECT id FROM sys_role WHERE role_code = {?varchar|code?}", Map.of("code", "DEFAULT"), 1);
            if (!defaultRole.isEmpty()) {
                String defaultRoleId = (String) defaultRole.get(0).get("id");
                SqlUtil.sync("web_user_role").insert(List.of(Map.of(
                        "id", UUID.randomUUID().toString().replace("-", ""),
                        "user_id", id,
                        "role_id", defaultRoleId
                ))).commit();
            }

            res.put("success", true);
            res.put("msg", "注册成功");
        } catch (Exception e) {
            res.put("error", "注册失败: " + e.getMessage());
        }
        return res;
    }

    @PostMapping("/page/login/api/migrate-passwords")
    public ResponseEntity<?> migratePasswords() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        List<Map<String, Object>> users;
        try {
            users = SqlUtil.query("SELECT user_name, password FROM web_user");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "查询失败: " + e.getMessage()));
        }

        int migrated = 0;
        int skipped = 0;

        for (Map<String, Object> user : users) {
            String username = (String) user.get("user_name");
            String rawPassword = (String) user.get("password");

            if (rawPassword != null && rawPassword.startsWith("$2a$")) {
                skipped++;
                continue;
            }

            String encoded = encoder.encode(rawPassword);
            try {
                SqlUtil.exec(
                        "UPDATE web_user SET password = {?varchar|mig_pass?} WHERE user_name = {?varchar|mig_user?}",
                        Map.of("mig_pass", encoded, "mig_user", username));
            } catch (Exception e) {
                return ResponseEntity.internalServerError()
                        .body(Map.of("error", "更新用户 " + username + " 失败: " + e.getMessage()));
            }
            migrated++;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("migrated", migrated);
        result.put("skipped", skipped);
        result.put("total", users.size());
        return ResponseEntity.ok(result);
    }
}
