package com.example.localPicmaService.security.controller;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.example.localPicmaService.common.DataSourceControl;
import com.example.localPicmaService.common.DramVariable;
import com.example.localPicmaService.security.JwtFilter;
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

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @PostMapping("/apilogin")
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

    @PostMapping("/api/register")
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

        JSONArray existing = DataSourceControl.runQuery(
                "SELECT user_name FROM web_user WHERE user_name = {?reg_user?}");
        if (!existing.isEmpty()) {
            res.put("error", "用户名已存在");
            return res;
        }

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String encodedPassword = encoder.encode(password);
        String id = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime now = LocalDateTime.now();

        DramVariable.set("reg_id", id);
        DramVariable.set("reg_user", username.trim());
        DramVariable.set("reg_pass", encodedPassword);
        DramVariable.set("reg_display", username.trim());
        DramVariable.set("reg_now", now.toString());
        DataSourceControl.runQuery(
                "INSERT INTO web_user (id, ver, create_date, update_date, del_flag, user_name, password, displayname, enabled, role) "
              + "VALUES ({?reg_id?}, 1, {?reg_now?}, {?reg_now?}, 0, {?reg_user?}, {?reg_pass?}, {?reg_display?}, 1, 'USER')");
        DramVariable.clear();

        res.put("success", true);
        res.put("msg", "注册成功");
        return res;
    }

    /**
     * 一次性密码迁移接口：将数据库中明文密码转为 BCrypt 加密格式。
     * 调用方式：POST /api/migrate-passwords  （无需认证）
     * 迁移完成后建议删除此接口或加回认证保护。
     */
    @PostMapping("/api/migrate-passwords")
    public ResponseEntity<?> migratePasswords() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        JSONArray users = DataSourceControl.runQuery(
                "SELECT user_name, password FROM web_user");

        int migrated = 0;
        int skipped = 0;

        for (int i = 0; i < users.size(); i++) {
            Map<String, Object> user = (Map<String, Object>) users.get(i);
            String username = (String) user.get("user_name");
            String rawPassword = (String) user.get("password");

            if (rawPassword != null && rawPassword.startsWith("$2a$")) {
                skipped++;
                continue;
            }

            String encoded = encoder.encode(rawPassword);
            DramVariable.set("mig_pass", encoded);
            DramVariable.set("mig_user", username);
            DataSourceControl.runQuery(
                    "UPDATE web_user SET password = {?mig_pass?} WHERE user_name = {?mig_user?}");
            DramVariable.clear();
            migrated++;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("migrated", migrated);
        result.put("skipped", skipped);
        result.put("total", users.size());
        return ResponseEntity.ok(result);
    }
}
