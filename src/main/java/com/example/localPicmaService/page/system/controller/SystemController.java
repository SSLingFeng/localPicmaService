package com.example.localPicmaService.page.system.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.example.localPicmaService.config.SystemConfig;
import com.example.localPicmaService.tool.RustFs.RustFsUtil;
import com.example.localPicmaService.tool.SQLTool.SqlUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 系统管理接口
 */
@RestController
@RequestMapping("/page/system/api")
public class SystemController {

    @Autowired
    private SystemConfig systemConfig;

    @Autowired
    private RustFsUtil rustFsUtil;

    // ======================== 系统配置 ========================

    @GetMapping("/config/list")
    public Map<String, Object> configList() throws Exception {
        String configPath = systemConfig.getConfigPath();
        if (configPath == null || configPath.isBlank()) {
            return Map.of("success", false, "error", "配置路径未初始化");
        }

        if (!FileUtil.exist(configPath)) {
            return Map.of("success", true, "items", List.of(), "path", configPath);
        }

        String json = FileUtil.readUtf8String(configPath);
        JSONObject config = JSONUtil.parseObj(json);

        List<Map<String, Object>> items = new ArrayList<>();
        for (String key : config.keySet()) {
            JSONObject entry = config.getJSONObject(key);
            if (entry == null) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", key);
            item.put("text", entry.getStr("text", ""));
            item.put("type", entry.getStr("type", ""));
            item.put("description", entry.getStr("zh-cn", entry.getStr("text", "")));
            item.put("value", entry.get("value"));
            items.add(item);
        }

        return Map.of("success", true, "items", items, "path", configPath);
    }

    @PostMapping("/config/save")
    public Map<String, Object> configSave(@RequestBody List<Map<String, Object>> items) throws Exception {
        String configPath = systemConfig.getConfigPath();
        if (configPath == null || configPath.isBlank()) {
            return Map.of("success", false, "error", "配置路径未初始化");
        }

        // 读取原始配置以保留 text/type/zh-cn
        String json = FileUtil.readUtf8String(configPath);
        JSONObject config = JSONUtil.parseObj(json);

        for (Map<String, Object> item : items) {
            String key = (String) item.get("key");
            Object value = item.get("value");
            if (key == null) continue;

            JSONObject entry = config.getJSONObject(key);
            if (entry == null) {
                entry = new JSONObject();
                entry.set("text", item.getOrDefault("text", key));
                entry.set("type", item.getOrDefault("type", "String"));
                entry.set("zh-cn", item.getOrDefault("description", key));
            }
            entry.set("value", value);
            config.set(key, entry);
        }

        FileUtil.writeString(config.toStringPretty(), configPath, StandardCharsets.UTF_8);

        // 热更新配置
        systemConfig.reload();

        return Map.of("success", true, "message", "配置已保存并热更新");
    }

    // ======================== 文件管理 ========================

    @GetMapping("/file/list")
    public Map<String, Object> fileList(@RequestParam(defaultValue = "0") int from,
                                        @RequestParam(defaultValue = "20") int size) throws Exception {
        List<Map<String, Object>> rows = SqlUtil.query(
                "SELECT id, file_name, file_format, file_size, access_url, rustfs_key, create_date "
                        + "FROM rustfs_file ORDER BY create_date DESC LIMIT " + size + " OFFSET " + from,
                Map.of(), size);

        Map<String, Object> countRow = SqlUtil.row("SELECT COUNT(*) AS cnt FROM rustfs_file", Map.of());
        long total = 0;
        if (countRow != null) {
            Object cnt = countRow.values().iterator().next();
            if (cnt instanceof Number) total = ((Number) cnt).longValue();
        }

        return Map.of("success", true, "items", rows != null ? rows : List.of(), "total", total);
    }

    @PostMapping("/file/delete")
    public Map<String, Object> fileDelete(@RequestBody Map<String, Object> body) throws Exception {
        String id = (String) body.get("id");
        if (id == null) return Map.of("success", false, "error", "缺少 id");

        try {
            boolean result = rustFsUtil.delete(id);
            return Map.of("success", result);
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    @PostMapping("/file/batch-delete")
    public Map<String, Object> fileBatchDelete(@RequestBody Map<String, Object> body) throws Exception {
        @SuppressWarnings("unchecked")
        List<String> ids = (List<String>) body.get("ids");
        if (ids == null || ids.isEmpty()) return Map.of("success", false, "error", "请选择文件");

        int deleted = 0;
        List<String> errors = new ArrayList<>();
        for (String id : ids) {
            try {
                if (rustFsUtil.delete(id)) deleted++;
            } catch (Exception e) {
                errors.add(id + ": " + e.getMessage());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("deleted", deleted);
        if (!errors.isEmpty()) result.put("errors", errors);
        return result;
    }

    @GetMapping("/file/preview")
    public void filePreview(@RequestParam String id,
                            jakarta.servlet.http.HttpServletResponse response) throws Exception {
        if (!rustFsUtil.isConfigured()) { response.setStatus(500); return; }
        try (java.io.InputStream is = rustFsUtil.download(id)) {
            Map<String, Object> info = rustFsUtil.getFileInfo(id);
            String contentType = "application/octet-stream";
            if (info != null) {
                String format = info.get("file_format") != null ? info.get("file_format").toString().toLowerCase() : "";
                if (format.matches("jpg|jpeg")) contentType = "image/jpeg";
                else if (format.equals("png")) contentType = "image/png";
                else if (format.equals("webp")) contentType = "image/webp";
                else if (format.equals("gif")) contentType = "image/gif";
                else if (format.equals("mp4")) contentType = "video/mp4";
                else if (format.equals("pdf")) contentType = "application/pdf";
            }
            response.setContentType(contentType);
            response.setHeader("Content-Disposition", "inline");
            java.io.OutputStream os = response.getOutputStream();
            byte[] buf = new byte[8192];
            int len;
            while ((len = is.read(buf)) != -1) os.write(buf, 0, len);
        } catch (Exception e) {
            response.setStatus(404);
        }
    }
}
