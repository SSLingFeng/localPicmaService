package com.example.localPicmaService.page.admin.controller;

import cn.hutool.json.JSONObject;
import com.example.localPicmaService.security.JwtFilter;
import com.example.localPicmaService.tool.SQLTool.SqlUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 后台管理接口 —— 角色管理、菜单管理、角色-菜单权限分配。
 */
@RestController
@RequestMapping("/page/admin/api")
public class AdminController {

    /** 超级管理员角色编码（与 JwtFilter 一致） */
    private static final String SUPER_ADMIN = JwtFilter.SUPER_ADMIN_ROLE;

    // ======================== 角色管理 ========================

    /** 获取所有角色列表（隐藏超级管理员角色） */
    @GetMapping("/roles")
    public List<Map<String, Object>> listRoles() throws Exception {
        List<Map<String, Object>> all = SqlUtil.query("SELECT * FROM sys_role ORDER BY sort_order");
        all.removeIf(r -> SUPER_ADMIN.equals(r.get("role_code")));
        return all;
    }

    /** 新增角色 */
    @PostMapping("/role/add")
    public Map<String, Object> addRole(@RequestBody JSONObject body) throws Exception {
        String name = body.getStr("role_name");
        String code = body.getStr("role_code");
        String desc = body.getStr("description");
        Integer sort = body.getInt("sort_order", 0);

        if (name == null || name.isBlank()) return Map.of("error", "角色名称不能为空");
        if (code == null || code.isBlank()) return Map.of("error", "角色编码不能为空");
        if (SUPER_ADMIN.equalsIgnoreCase(code.trim())) return Map.of("error", "该角色编码为系统保留");

        List<Map<String, Object>> exists = SqlUtil.query(
                "SELECT id FROM sys_role WHERE role_code = {?varchar|code?}", Map.of("code", code), 1);
        if (!exists.isEmpty()) return Map.of("error", "角色编码已存在");

        String id = UUID.randomUUID().toString().replace("-", "");
        Date now = new Date();
        SqlUtil.sync("sys_role").insert(List.of(Map.of(
                "id", id, "role_name", name, "role_code", code.toUpperCase(),
                "description", desc != null ? desc : "",
                "sort_order", sort, "enabled", 1,
                "create_date", now, "update_date", now
        ))).commit();
        return Map.of("success", true, "id", id);
    }

    /** 更新角色 */
    @PostMapping("/role/update")
    public Map<String, Object> updateRole(@RequestBody JSONObject body) throws Exception {
        String id = body.getStr("id");
        if (id == null) return Map.of("error", "角色ID不能为空");

        List<Map<String, Object>> list = SqlUtil.query(
                "SELECT * FROM sys_role WHERE id = {?varchar|id?}", Map.of("id", id), 1);
        if (list.isEmpty()) return Map.of("error", "角色不存在");

        Map<String, Object> row = list.get(0);
        row.put("role_name", body.getStr("role_name"));
        row.put("description", body.getStr("description"));
        row.put("sort_order", body.getInt("sort_order", 0));
        row.put("enabled", body.getInt("enabled", 1));
        row.put("update_date", new Date());
        row.put("$id", id);

        SqlUtil.sync("sys_role").update(List.of(row)).commit();
        return Map.of("success", true);
    }

    /** 删除角色 */
    @PostMapping("/role/delete")
    public Map<String, Object> deleteRole(@RequestBody JSONObject body) throws Exception {
        String id = body.getStr("id");
        if (id == null) return Map.of("error", "角色ID不能为空");

        SqlUtil.exec("DELETE FROM sys_role_menu WHERE role_id = {?varchar|id?}", Map.of("id", id));
        SqlUtil.exec("DELETE FROM sys_role WHERE id = {?varchar|id?}", Map.of("id", id));
        return Map.of("success", true);
    }

    // ======================== 前端导航菜单 ========================

    @GetMapping("/nav/menus")
    public List<Map<String, Object>> navMenus(HttpServletRequest request) throws Exception {
        String username = getUsernameFromToken(request);
        if (username == null) return Collections.emptyList();

        // 1. 查询用户的所有角色
        List<Map<String, Object>> userRoles = SqlUtil.query(
                "SELECT r.role_code FROM web_user_role ur JOIN sys_role r ON ur.role_id = r.id "
              + "JOIN web_user u ON ur.user_id = u.id WHERE u.user_name = {?varchar|u?}",
                Map.of("u", username));

        // 同时兼容 web_user.role 字段（旧的单角色字段）
        Map<String, Object> userRow = SqlUtil.row(
                "SELECT role FROM web_user WHERE user_name = {?varchar|u?}", Map.of("u", username));
        Set<String> allRoleCodes = new LinkedHashSet<>();
        for (Map<String, Object> r : userRoles) {
            String rc = (String) r.get("role_code");
            if (rc != null) allRoleCodes.add(rc);
        }
        if (userRow != null) {
            String legacyRole = (String) userRow.get("role");
            if (legacyRole != null && !legacyRole.isEmpty()) allRoleCodes.add(legacyRole);
        }

        // 2. 超级管理员（SSLingFengDev）→ 返回全部菜单（大小写不敏感）
        boolean isSuperAdmin = allRoleCodes.stream().anyMatch(rc -> SUPER_ADMIN.equalsIgnoreCase(rc));
        System.out.println(">>> navMenus 用户: " + username + " 角色: " + allRoleCodes + " 超管: " + isSuperAdmin);
        if (isSuperAdmin) {
            return SqlUtil.query("SELECT id, menu_name, path, icon, parent_id, sort_order, is_folder FROM sys_menu WHERE enabled = 1 ORDER BY sort_order");
        }

        // 3. 普通用户 → 合并所有角色的菜单（去重），并递归查找父菜单
        if (allRoleCodes.isEmpty()) return Collections.emptyList();

        List<String> roleCodes = new ArrayList<>(allRoleCodes);
        StringBuilder inClause = new StringBuilder();
        Map<String, Object> params = new HashMap<>();
        for (int i = 0; i < roleCodes.size(); i++) {
            if (i > 0) inClause.append(",");
            String key = "rc" + i;
            inClause.append("{?varchar|").append(key).append("?}");
            params.put(key, roleCodes.get(i));
        }

        String sql =
                "WITH RECURSIVE menu_tree AS ("
              + "  SELECT id, parent_id FROM sys_menu WHERE id IN ("
              + "    SELECT rm.menu_id FROM sys_role_menu rm JOIN sys_role r ON rm.role_id = r.id WHERE r.role_code IN (" + inClause + ")"
              + "  )"
              + "  UNION"
              + "  SELECT m.id, m.parent_id FROM sys_menu m JOIN menu_tree mt ON m.id = mt.parent_id WHERE mt.parent_id <> '0'"
              + ") SELECT DISTINCT sm.id, sm.menu_name, sm.path, sm.icon, sm.parent_id, sm.sort_order, sm.is_folder"
              + " FROM sys_menu sm JOIN menu_tree mt ON sm.id = mt.id"
              + " WHERE sm.enabled = 1 ORDER BY sm.sort_order";

        return SqlUtil.query(sql, params);
    }

    /** 从JWT中提取用户名 */
    private String getUsernameFromToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                cn.hutool.jwt.JWT jwt = JwtFilter.parseAndVerify(authHeader.substring(7));
                return (String) jwt.getPayload("username");
            } catch (Exception e) { return null; }
        }
        return null;
    }

    // ======================== 菜单管理 ========================

    @GetMapping("/menus")
    public List<Map<String, Object>> listMenus() throws Exception {
        return SqlUtil.query("SELECT * FROM sys_menu ORDER BY sort_order");
    }

    @PostMapping("/menu/add")
    public Map<String, Object> addMenu(@RequestBody JSONObject body) throws Exception {
        String name = body.getStr("menu_name");
        String code = body.getStr("menu_code");
        if (name == null || name.isBlank()) return Map.of("error", "菜单名称不能为空");
        if (code == null || code.isBlank()) return Map.of("error", "菜单编码不能为空");

        List<Map<String, Object>> exists = SqlUtil.query(
                "SELECT id FROM sys_menu WHERE menu_code = {?varchar|code?}", Map.of("code", code), 1);
        if (!exists.isEmpty()) return Map.of("error", "菜单编码已存在");

        String id = UUID.randomUUID().toString().replace("-", "");
        String parentId = body.getStr("parent_id");
        if (parentId == null || parentId.isBlank()) parentId = "0";
        Integer isFolder = body.getInt("is_folder", 0);
        Date now = new Date();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("menu_name", name);
        row.put("menu_code", code);
        row.put("path", body.getStr("path") != null ? body.getStr("path") : "");
        row.put("icon", body.getStr("icon") != null ? body.getStr("icon") : "");
        row.put("parent_id", parentId);
        row.put("sort_order", body.getInt("sort_order", 0));
        row.put("is_folder", isFolder);
        row.put("enabled", 1);
        row.put("create_date", now);
        row.put("update_date", now);
        SqlUtil.sync("sys_menu").insert(List.of(row)).commit();
        return Map.of("success", true, "id", id);
    }

    @PostMapping("/menu/update")
    public Map<String, Object> updateMenu(@RequestBody JSONObject body) throws Exception {
        String id = body.getStr("id");
        if (id == null) return Map.of("error", "菜单ID不能为空");

        List<Map<String, Object>> list = SqlUtil.query(
                "SELECT * FROM sys_menu WHERE id = {?varchar|id?}", Map.of("id", id), 1);
        if (list.isEmpty()) return Map.of("error", "菜单不存在");

        Map<String, Object> row = list.get(0);
        row.put("menu_name", body.getStr("menu_name"));
        row.put("path", body.getStr("path"));
        row.put("icon", body.getStr("icon"));
        String parentId = body.getStr("parent_id");
        row.put("parent_id", (parentId != null && !parentId.isBlank()) ? parentId : "0");
        row.put("sort_order", body.getInt("sort_order", 0));
        row.put("is_folder", body.getInt("is_folder", 0));
        row.put("enabled", body.getInt("enabled", 1));
        row.put("update_date", new Date());
        row.put("$id", id);

        SqlUtil.sync("sys_menu").update(List.of(row)).commit();
        return Map.of("success", true);
    }

    @PostMapping("/menu/delete")
    public Map<String, Object> deleteMenu(@RequestBody JSONObject body) throws Exception {
        String id = body.getStr("id");
        if (id == null) return Map.of("error", "菜单ID不能为空");

        SqlUtil.exec("DELETE FROM sys_role_menu WHERE menu_id = {?varchar|id?}", Map.of("id", id));
        SqlUtil.exec("DELETE FROM sys_menu WHERE id = {?varchar|id?}", Map.of("id", id));
        return Map.of("success", true);
    }

    // ======================== 权限分配 ========================

    @GetMapping("/role-menus")
    public Map<String, Object> getRoleMenus(@RequestParam String role_id) throws Exception {
        List<Map<String, Object>> list = SqlUtil.query(
                "SELECT menu_id FROM sys_role_menu WHERE role_id = {?varchar|rid?}", Map.of("rid", role_id));
        List<String> menuIds = new ArrayList<>();
        for (Map<String, Object> row : list) menuIds.add((String) row.get("menu_id"));
        return Map.of("role_id", role_id, "menu_ids", menuIds);
    }

    @PostMapping("/role-menus/save")
    public Map<String, Object> saveRoleMenus(@RequestBody JSONObject body) throws Exception {
        String roleId = body.getStr("role_id");
        if (roleId == null) return Map.of("error", "角色ID不能为空");

        SqlUtil.exec("DELETE FROM sys_role_menu WHERE role_id = {?varchar|rid?}", Map.of("rid", roleId));

        List<String> menuIds = body.getJSONArray("menu_ids").toList(String.class);
        if (menuIds != null && !menuIds.isEmpty()) {
            List<Map<String, Object>> insertList = new ArrayList<>();
            for (String menuId : menuIds) {
                insertList.add(Map.of(
                        "id", UUID.randomUUID().toString().replace("-", ""),
                        "role_id", roleId, "menu_id", menuId));
            }
            SqlUtil.sync("sys_role_menu").insert(insertList).commit();
        }
        return Map.of("success", true);
    }

    @GetMapping("/users")
    public List<Map<String, Object>> listUsers() throws Exception {
        List<Map<String, Object>> users = SqlUtil.query(
                "SELECT id, user_name, displayname, role, enabled FROM web_user ORDER BY create_date");
        for (Map<String, Object> user : users) {
            String uid = (String) user.get("id");
            List<Map<String, Object>> roleRows = SqlUtil.query(
                    "SELECT r.role_code FROM web_user_role ur JOIN sys_role r ON ur.role_id = r.id WHERE ur.user_id = {?varchar|uid?}",
                    Map.of("uid", uid));
            List<String> roleCodes = new ArrayList<>();
            for (Map<String, Object> rr : roleRows) roleCodes.add((String) rr.get("role_code"));
            String legacyRole = (String) user.get("role");
            if (legacyRole != null && !legacyRole.isEmpty() && !roleCodes.contains(legacyRole)) {
                roleCodes.add(legacyRole);
            }
            user.put("roles", roleCodes);
        }
        return users;
    }

    @PostMapping("/user/delete")
    public Map<String, Object> deleteUser(@RequestBody JSONObject body) throws Exception {
        String id = body.getStr("id");
        if (id == null) return Map.of("error", "用户ID不能为空");

        List<Map<String, Object>> list = SqlUtil.query(
                "SELECT user_name, role FROM web_user WHERE id = {?varchar|id?}", Map.of("id", id), 1);
        if (list.isEmpty()) return Map.of("error", "用户不存在");

        Map<String, Object> user = list.get(0);
        String role = (String) user.get("role");
        if (SUPER_ADMIN.equalsIgnoreCase(role)) {
            return Map.of("error", "不能删除超级管理员");
        }

        SqlUtil.exec("DELETE FROM web_user_role WHERE user_id = {?varchar|id?}", Map.of("id", id));
        SqlUtil.exec("DELETE FROM web_user WHERE id = {?varchar|id?}", Map.of("id", id));
        return Map.of("success", true);
    }

    @PostMapping("/user-roles/save")
    public Map<String, Object> saveUserRoles(@RequestBody JSONObject body) throws Exception {
        String userId = body.getStr("user_id");
        if (userId == null) return Map.of("error", "用户ID不能为空");

        SqlUtil.exec("DELETE FROM web_user_role WHERE user_id = {?varchar|uid?}", Map.of("uid", userId));

        List<String> roleCodes = body.getJSONArray("role_codes").toList(String.class);
        if (roleCodes != null) roleCodes.removeIf(c -> SUPER_ADMIN.equalsIgnoreCase(c));
        if (roleCodes != null && !roleCodes.isEmpty()) {
            List<Map<String, Object>> allRoles = SqlUtil.query("SELECT id, role_code FROM sys_role");
            Map<String, String> codeToId = new HashMap<>();
            for (Map<String, Object> r : allRoles) codeToId.put((String) r.get("role_code"), (String) r.get("id"));

            List<Map<String, Object>> insertList = new ArrayList<>();
            for (String code : roleCodes) {
                String roleId = codeToId.get(code);
                if (roleId != null) {
                    insertList.add(Map.of(
                            "id", UUID.randomUUID().toString().replace("-", ""),
                            "user_id", userId, "role_id", roleId));
                }
            }
            if (!insertList.isEmpty()) {
                SqlUtil.sync("web_user_role").insert(insertList).commit();
            }

            String firstRole = roleCodes.get(0);
            SqlUtil.exec("UPDATE web_user SET role = {?varchar|r?} WHERE id = {?varchar|u?}",
                    Map.of("r", firstRole, "u", userId));
        } else {
            SqlUtil.exec("UPDATE web_user SET role = '' WHERE id = {?varchar|u?}", Map.of("u", userId));
        }
        return Map.of("success", true);
    }
}
