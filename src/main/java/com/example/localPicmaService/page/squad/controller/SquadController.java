package com.example.localPicmaService.page.squad.controller;

import cn.hutool.json.JSONObject;
import com.example.localPicmaService.tool.SQLTool.SqlUtil;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 战队管理接口 —— 战队/成员 CRUD 管理接口（供后台管理页面使用）。
 */
@RestController
@RequestMapping("/page/squad/api/admin")
public class SquadController {

    // ======================== 战队 CRUD ========================

    @GetMapping("/list")
    public List<Map<String, Object>> listSquads() throws Exception {
        return SqlUtil.query("SELECT * FROM squad ORDER BY nick");
    }

    @PostMapping("/add")
    public Map<String, Object> addSquad(@RequestBody JSONObject body) throws Exception {
        String nick = body.getStr("nick");
        String name = body.getStr("name");
        if (nick == null || nick.isBlank()) return Map.of("error", "战队缩写不能为空");
        if (name == null || name.isBlank()) return Map.of("error", "战队名称不能为空");

        List<Map<String, Object>> exists = SqlUtil.query(
                "SELECT id FROM squad WHERE nick = {?varchar|n?}", Map.of("n", nick), 1);
        if (!exists.isEmpty()) return Map.of("error", "战队缩写已存在");

        String id = UUID.randomUUID().toString().replace("-", "");
        Date now = new Date();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        data.put("nick", nick);
        data.put("name", name);
        data.put("title", body.getStr("title") != null ? body.getStr("title") : "");
        data.put("email", body.getStr("email") != null ? body.getStr("email") : "");
        data.put("web", body.getStr("web") != null ? body.getStr("web") : "");
        data.put("picture", body.getStr("picture") != null ? body.getStr("picture") : "");
        data.put("enabled", 1);
        data.put("create_date", now);
        data.put("update_date", now);

        SqlUtil.sync("squad").insert(List.of(data)).commit();
        return Map.of("success", true, "id", id);
    }

    @PostMapping("/update")
    public Map<String, Object> updateSquad(@RequestBody JSONObject body) throws Exception {
        String id = body.getStr("id");
        if (id == null) return Map.of("error", "战队ID不能为空");

        List<Map<String, Object>> list = SqlUtil.query(
                "SELECT * FROM squad WHERE id = {?varchar|id?}", Map.of("id", id), 1);
        if (list.isEmpty()) return Map.of("error", "战队不存在");

        Map<String, Object> row = list.get(0);
        row.put("nick", body.getStr("nick"));
        row.put("name", body.getStr("name"));
        row.put("title", body.getStr("title"));
        row.put("email", body.getStr("email"));
        row.put("web", body.getStr("web"));
        row.put("picture", body.getStr("picture"));
        row.put("enabled", body.getInt("enabled", 1));
        row.put("update_date", new Date());
        row.put("$id", id);

        SqlUtil.sync("squad").update(List.of(row)).commit();
        return Map.of("success", true);
    }

    @PostMapping("/delete")
    public Map<String, Object> deleteSquad(@RequestBody JSONObject body) throws Exception {
        String id = body.getStr("id");
        if (id == null) return Map.of("error", "战队ID不能为空");
        SqlUtil.exec("DELETE FROM squad_member WHERE squad_id = {?varchar|sid?}", Map.of("sid", id));
        SqlUtil.exec("DELETE FROM squad WHERE id = {?varchar|id?}", Map.of("id", id));
        return Map.of("success", true);
    }

    // ======================== 成员 CRUD ========================

    @GetMapping("/users")
    public List<Map<String, Object>> listUsers() throws Exception {
        return SqlUtil.query(
                "SELECT id, user_name, displayname FROM web_user ORDER BY user_name");
    }

    @GetMapping("/members")
    public List<Map<String, Object>> listMembers(@RequestParam String squad_id) throws Exception {
        return SqlUtil.query(
                "SELECT sm.*, u.user_name, u.displayname AS user_displayname "
              + "FROM squad_member sm LEFT JOIN web_user u ON sm.user_id = u.id "
              + "WHERE sm.squad_id = {?varchar|sid?} ORDER BY sm.sort_order",
                Map.of("sid", squad_id));
    }

    @PostMapping("/member/add")
    public Map<String, Object> addMember(@RequestBody JSONObject body) throws Exception {
        String squadId = body.getStr("squad_id");
        if (squadId == null) return Map.of("error", "战队ID不能为空");
        String userId = body.getStr("user_id");
        if (userId == null || userId.isEmpty()) return Map.of("error", "请选择用户");

        List<Map<String, Object>> users = SqlUtil.query(
                "SELECT id, user_name, displayname FROM web_user WHERE id = {?varchar|uid?}",
                Map.of("uid", userId), 1);
        if (users.isEmpty()) return Map.of("error", "用户不存在");
        Map<String, Object> user = users.get(0);

        List<Map<String, Object>> exists = SqlUtil.query(
                "SELECT id FROM squad_member WHERE squad_id = {?varchar|sid?} AND user_id = {?varchar|uid?}",
                Map.of("sid", squadId, "uid", userId), 1);
        if (!exists.isEmpty()) return Map.of("error", "该用户已是战队成员");

        String id = UUID.randomUUID().toString().replace("-", "");
        Date now = new Date();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        data.put("squad_id", squadId);
        data.put("user_id", userId);
        data.put("steam_id", body.getStr("steam_id") != null ? body.getStr("steam_id") : "");
        data.put("nick", body.getStr("nick") != null ? body.getStr("nick") : "");
        data.put("name", user.get("displayname") != null ? user.get("displayname") : user.get("user_name"));
        data.put("email", "");
        data.put("icq", body.getStr("icq") != null ? body.getStr("icq") : "");
        data.put("remark", body.getStr("remark") != null ? body.getStr("remark") : "");
        data.put("sort_order", body.getInt("sort_order", 0));
        data.put("enabled", 1);
        data.put("create_date", now);
        data.put("update_date", now);

        SqlUtil.sync("squad_member").insert(List.of(data)).commit();
        return Map.of("success", true, "id", id);
    }

    @PostMapping("/member/update")
    public Map<String, Object> updateMember(@RequestBody JSONObject body) throws Exception {
        String id = body.getStr("id");
        if (id == null) return Map.of("error", "成员ID不能为空");

        List<Map<String, Object>> list = SqlUtil.query(
                "SELECT * FROM squad_member WHERE id = {?varchar|id?}", Map.of("id", id), 1);
        if (list.isEmpty()) return Map.of("error", "成员不存在");

        Map<String, Object> row = list.get(0);
        row.put("steam_id", body.getStr("steam_id"));
        row.put("nick", body.getStr("nick"));
        row.put("icq", body.getStr("icq"));
        row.put("remark", body.getStr("remark"));
        row.put("sort_order", body.getInt("sort_order", 0));
        row.put("enabled", body.getInt("enabled", 1));
        row.put("update_date", new Date());
        row.put("$id", id);

        SqlUtil.sync("squad_member").update(List.of(row)).commit();
        return Map.of("success", true);
    }

    @PostMapping("/member/delete")
    public Map<String, Object> deleteMember(@RequestBody JSONObject body) throws Exception {
        String id = body.getStr("id");
        if (id == null) return Map.of("error", "成员ID不能为空");
        SqlUtil.exec("DELETE FROM squad_member WHERE id = {?varchar|id?}", Map.of("id", id));
        return Map.of("success", true);
    }
}
