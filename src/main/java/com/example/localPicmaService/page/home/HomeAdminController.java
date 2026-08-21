package com.example.localPicmaService.page.home;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.example.localPicmaService.tool.RustFs.RustFsUtil;
import com.example.localPicmaService.tool.SQLTool.SqlUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

/**
 * 首页内容管理接口
 */
@RestController
@RequestMapping("/home/admin/api")
public class HomeAdminController {

    @Autowired
    private RustFsUtil rustFsUtil;

    // ======================== 模块配置 ========================

    /** 获取所有模块配置 */
    @GetMapping("/configs")
    public Map<String, Object> getConfigs() throws Exception {
        List<Map<String, Object>> rows = SqlUtil.query(
                "SELECT * FROM home_module_config ORDER BY id", Map.of(), 20);
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                Object enabled = row.get("enabled");
                if (enabled instanceof Number) {
                    row.put("enabled", ((Number) enabled).intValue() != 0);
                }
            }
        }
        return Map.of("success", true, "items", rows != null ? rows : List.of());
    }

    /** 更新模块配置（max_display / enabled） */
    @PostMapping("/config/save")
    public Map<String, Object> saveConfig(@RequestBody JSONObject body) throws Exception {
        String moduleType = body.getStr("module_type");
        Integer maxDisplay = body.getInt("max_display");
        Boolean enabled = body.getBool("enabled");
        if (moduleType == null) return Map.of("success", false, "error", "缺少 module_type");

        Map<String, Object> params = new LinkedHashMap<>();
        List<String> sets = new ArrayList<>();
        int idx = 0;

        if (maxDisplay != null) {
            sets.add("max_display = {?int|p" + idx + "?}");
            params.put("p" + idx, maxDisplay);
            idx++;
        }
        if (enabled != null) {
            sets.add("enabled = {?boolean|p" + idx + "?}");
            params.put("p" + idx, enabled);
            idx++;
        }
        sets.add("update_date = NOW()");
        params.put("p" + idx, moduleType);

        SqlUtil.exec("UPDATE home_module_config SET " + String.join(", ", sets)
                + " WHERE module_type = {?varchar|p" + idx + "?}", params);
        return Map.of("success", true);
    }

    // ======================== 内容管理 ========================

    /** 获取指定模块的内容列表 */
    @GetMapping("/list")
    public Map<String, Object> list(@RequestParam String moduleType) throws Exception {
        List<Map<String, Object>> rows = SqlUtil.query(
                "SELECT id, module_type, title, content, order_num, date_time, data::text as data, "
                        + "sort_order, create_date, update_date "
                        + "FROM home_content WHERE module_type = {?varchar|m?} ORDER BY sort_order, id",
                Map.of("m", moduleType), 200);
        // 解析 JSONB data（图片ID数组）
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                Object data = row.get("data");
                if (data != null) {
                    try {
                        row.put("data", JSONUtil.parseArray(data.toString()));
                    } catch (Exception ignored) {}
                }
            }
        }
        return Map.of("success", true, "items", rows != null ? rows : List.of());
    }

    /** 新增内容 */
    @PostMapping("/add")
    public Map<String, Object> add(@RequestBody JSONObject body) throws Exception {
        String moduleType = body.getStr("module_type");
        String title = body.getStr("title");
        String content = body.getStr("content");
        Integer orderNum = body.getInt("order_num");
        String dateTime = body.getStr("date_time");
        Object images = body.get("images");  // [{file_id, order_num}]
        int sortOrder = body.getInt("sort_order", 0);
        if (moduleType == null) return Map.of("success", false, "error", "参数不完整");

        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", id);
        params.put("m", moduleType);
        params.put("s", sortOrder);

        StringBuilder cols = new StringBuilder("id, module_type, sort_order");
        StringBuilder vals = new StringBuilder("{?varchar|id?}, {?varchar|m?}, {?int|s?}");

        if (title != null) {
            cols.append(", title");
            vals.append(", {?varchar|title?}");
            params.put("title", title);
        }
        if (content != null) {
            cols.append(", content");
            vals.append(", {?varchar|content?}");
            params.put("content", content);
        }
        if (orderNum != null) {
            cols.append(", order_num");
            vals.append(", {?int|orderNum?}");
            params.put("orderNum", orderNum);
        }
        if (dateTime != null) {
            cols.append(", date_time");
            vals.append(", {?varchar|dateTime?}");
            params.put("dateTime", dateTime);
        }
        if (images != null) {
            cols.append(", data");
            vals.append(", {?varchar|data?}::jsonb");
            params.put("data", JSONUtil.toJsonStr(images));
        }

        SqlUtil.exec("INSERT INTO home_content (" + cols + ") VALUES (" + vals + ")", params);
        return Map.of("success", true, "id", id);
    }

    /** 更新内容 */
    @PostMapping("/update")
    public Map<String, Object> update(@RequestBody JSONObject body) throws Exception {
        String id = body.getStr("id");
        if (id == null) return Map.of("success", false, "error", "缺少 id");

        String title = body.getStr("title");
        String content = body.getStr("content");
        Integer orderNum = body.getInt("order_num");
        String dateTime = body.getStr("date_time");
        Object images = body.get("images");
        Integer sortOrder = body.getInt("sort_order");

        Map<String, Object> params = new LinkedHashMap<>();
        List<String> sets = new ArrayList<>();
        int idx = 0;

        sets.add("title = {?varchar|p" + idx + "?}");
        params.put("p" + idx, title != null ? title : "");
        idx++;

        sets.add("content = {?varchar|p" + idx + "?}");
        params.put("p" + idx, content != null ? content : "");
        idx++;

        sets.add("order_num = {?int|p" + idx + "?}");
        params.put("p" + idx, orderNum != null ? orderNum : 0);
        idx++;

        sets.add("date_time = {?varchar|p" + idx + "?}");
        params.put("p" + idx, dateTime != null ? dateTime : "");
        idx++;

        if (images != null) {
            sets.add("data = {?varchar|p" + idx + "?}::jsonb");
            params.put("p" + idx, JSONUtil.toJsonStr(images));
            idx++;
        }
        if (sortOrder != null) {
            sets.add("sort_order = {?int|p" + idx + "?}");
            params.put("p" + idx, sortOrder);
            idx++;
        }
        sets.add("update_date = NOW()");
        params.put("p" + idx, id);

        SqlUtil.exec("UPDATE home_content SET " + String.join(", ", sets)
                + " WHERE id = {?varchar|p" + idx + "?}", params);
        return Map.of("success", true);
    }

    /** 删除内容 */
    @PostMapping("/delete")
    public Map<String, Object> delete(@RequestBody JSONObject body) throws Exception {
        String id = body.getStr("id");
        if (id == null) return Map.of("success", false, "error", "缺少 id");
        SqlUtil.exec("DELETE FROM home_content WHERE id = {?varchar|id?}", Map.of("id", id));
        return Map.of("success", true);
    }

    // ======================== 图片上传（RustFS） ========================

    /** 上传图片到 RustFS，返回 rustfs_file 表 ID */
    @PostMapping("/upload-image")
    public Map<String, Object> uploadImage(@RequestParam("file") MultipartFile file,
                                           @RequestParam("moduleType") String moduleType) {
        if (!rustFsUtil.isConfigured()) return Map.of("success", false, "error", "RustFS 未配置");
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return Map.of("success", false, "error", "仅支持图片文件");
        }
        try {
            String ext = "";
            String origName = file.getOriginalFilename();
            if (origName != null && origName.contains(".")) {
                ext = origName.substring(origName.lastIndexOf('.'));
            }
            String objectPath = "home/" + moduleType + "/" + System.currentTimeMillis() + ext;
            String dbId = rustFsUtil.upload(file.getInputStream(), objectPath, origName, contentType);
            return Map.of("success", true, "id", dbId);
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }
}
