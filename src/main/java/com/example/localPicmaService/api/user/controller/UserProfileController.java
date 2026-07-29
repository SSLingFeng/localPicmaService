package com.example.localPicmaService.api.user.controller;

import cn.hutool.json.JSONObject;
import com.example.localPicmaService.security.JwtFilter;
import com.example.localPicmaService.tool.SQLTool.SqlUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserProfileController {

    /**
     * 获取当前登录用户的个人信息
     */
    @GetMapping("/info")
    public ResponseEntity<?> getInfo(HttpServletRequest request) {
        String username = getCurrentUsername(request);
        if (username == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }

        try {
            Map<String, Object> user = SqlUtil.row(
                    "SELECT id, user_name, displayname, qq_number, steam_uuid, role, enabled FROM web_user WHERE user_name = {?varchar|u?}",
                    Map.of("u", username));
            if (user == null) {
                return ResponseEntity.status(404).body(Map.of("error", "用户不存在"));
            }
            return ResponseEntity.ok(user);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "查询失败: " + e.getMessage()));
        }
    }

    /**
     * 更新当前登录用户的个人信息（displayname, qq_number, steam_uuid）
     */
    @PostMapping("/update")
    public ResponseEntity<?> updateInfo(@RequestBody JSONObject body, HttpServletRequest request) {
        String username = getCurrentUsername(request);
        if (username == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }

        String displayname = body.getStr("displayname");
        String qqNumber = body.getStr("qq_number");
        String steamUuid = body.getStr("steam_uuid");

        if (displayname == null || displayname.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "用户名称不能为空"));
        }
        if (qqNumber != null && !qqNumber.isEmpty() && !qqNumber.matches("^\\d{1,14}$")) {
            return ResponseEntity.badRequest().body(Map.of("error", "QQ号仅限数字，最长14位"));
        }
        if (steamUuid != null && !steamUuid.isEmpty() && !steamUuid.matches("^\\d{1,20}$")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Steam ID仅限数字，最长20位"));
        }

        try {
            SqlUtil.exec(
                    "UPDATE web_user SET displayname = {?varchar|dn?}, qq_number = {?varchar|qq?}, steam_uuid = {?varchar|su?} WHERE user_name = {?varchar|u?}",
                    Map.of("dn", displayname != null ? displayname : "",
                           "qq", qqNumber != null ? qqNumber : "",
                           "su", steamUuid != null ? steamUuid : "",
                           "u", username));
            return ResponseEntity.ok(Map.of("success", true, "msg", "更新成功"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "更新失败: " + e.getMessage()));
        }
    }

    /**
     * 修改密码
     */
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody JSONObject body, HttpServletRequest request) {
        String username = getCurrentUsername(request);
        if (username == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }

        String oldPassword = body.getStr("oldPassword");
        String newPassword = body.getStr("newPassword");

        if (oldPassword == null || oldPassword.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "请输入原密码"));
        }
        if (newPassword == null || newPassword.length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("error", "新密码长度不能少于6位"));
        }

        try {
            Map<String, Object> user = SqlUtil.row(
                    "SELECT password FROM web_user WHERE user_name = {?varchar|u?}",
                    Map.of("u", username));
            if (user == null) {
                return ResponseEntity.status(404).body(Map.of("error", "用户不存在"));
            }

            String storedPassword = (String) user.get("password");
            BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
            if (!encoder.matches(oldPassword, storedPassword)) {
                return ResponseEntity.badRequest().body(Map.of("error", "原密码错误"));
            }

            String encodedNew = encoder.encode(newPassword);
            SqlUtil.exec("UPDATE web_user SET password = {?varchar|p?} WHERE user_name = {?varchar|u?}",
                    Map.of("p", encodedNew, "u", username));

            return ResponseEntity.ok(Map.of("success", true, "msg", "密码修改成功"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "修改失败: " + e.getMessage()));
        }
    }

    private String getCurrentUsername(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                cn.hutool.jwt.JWT jwt = JwtFilter.parseAndVerify(authHeader.substring(7));
                return (String) jwt.getPayload("username");
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}
