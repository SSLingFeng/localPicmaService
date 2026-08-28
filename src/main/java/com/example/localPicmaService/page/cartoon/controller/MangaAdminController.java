package com.example.localPicmaService.page.cartoon.controller;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.example.localPicmaService.config.SystemConfig;
import com.example.localPicmaService.tool.SQLTool.SqlUtil;
import com.example.localPicmaService.tool.Valkey.ValkeyUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 漫画管理员后台接口
 */
@RestController
@RequestMapping("/page/cartoon/admin/api")
public class MangaAdminController {

    @Autowired
    private SystemConfig systemConfig;

    @Autowired
    private ValkeyUtil valkeyUtil;

    private static final Map<String, String> TYPE_DIR = Map.of(
            "漫画", "cartoon",
            "coser", "coser"
    );

    // ======================== 漫画列表（含已删除） ========================

    @PostMapping("/list")
    public Map<String, Object> list(@RequestBody JSONObject body) throws Exception {
        int from = body.getInt("_from", 0);
        int size = body.getInt("size", 12);
        JSONObject params = body.getJSONObject("params");
        String searchTitle = params != null ? params.getStr("searchTitle", "") : "";
        String searchType = params != null ? params.getStr("searchType", "") : "";
        List<String> searchTags = params != null ? params.getJSONArray("searchtags") != null ?
                params.getJSONArray("searchtags").toList(String.class) : List.of() : List.of();
        List<String> searchCats = params != null ? params.getJSONArray("searchCategories") != null ?
                params.getJSONArray("searchCategories").toList(String.class) : List.of() : List.of();
        String sortField = params != null ? params.getStr("sortField", "") : "";
        String sortOrder = params != null ? params.getStr("sortOrder", "desc") : "desc";
        Integer filterDel = params != null ? params.getInt("filterDel") : null;

        List<String> conditions = new ArrayList<>();
        Map<String, Object> queryParams = new LinkedHashMap<>();
        int paramIdx = 0;

        // 管理员可选过滤：0=仅正常, 1=仅已删除, null=全部
        if (filterDel != null) {
            conditions.add("del_flag = {?int|p" + paramIdx + "?}");
            queryParams.put("p" + paramIdx, filterDel);
            paramIdx++;
        }

        if (searchTitle != null && !searchTitle.isBlank()) {
            conditions.add("title ILIKE {?varchar|p" + paramIdx + "?}");
            queryParams.put("p" + paramIdx, "%" + searchTitle + "%");
            paramIdx++;
        }
        if (searchType != null && !searchType.isBlank()) {
            conditions.add("type = {?varchar|p" + paramIdx + "?}");
            queryParams.put("p" + paramIdx, searchType);
            paramIdx++;
        }
        for (String tag : searchTags) {
            conditions.add("tags::jsonb @> {?varchar|p" + paramIdx + "?}::jsonb");
            queryParams.put("p" + paramIdx, "[\"" + tag + "\"]");
            paramIdx++;
        }
        for (String cat : searchCats) {
            conditions.add("categories::jsonb @> {?varchar|p" + paramIdx + "?}::jsonb");
            queryParams.put("p" + paramIdx, "[\"" + cat + "\"]");
            paramIdx++;
        }

        String where = conditions.isEmpty() ? "1=1" : String.join(" AND ", conditions);

        String countSql = "SELECT COUNT(*) AS cnt FROM manga_source WHERE " + where;
        Map<String, Object> countRow = SqlUtil.row(countSql, queryParams);
        long total = 0;
        if (countRow != null) {
            Object cnt = countRow.values().iterator().next();
            if (cnt instanceof Number) total = ((Number) cnt).longValue();
        }

        String orderBy = buildOrderBy(sortField, sortOrder);

        String dataSql = "SELECT id, type, title, author, chinese_team, description, "
                + "tags, categories, pages_count, chapters, likes, comments, time, path, directory, "
                + "del_flag, del_reason "
                + "FROM manga_source WHERE " + where + " " + orderBy + " LIMIT " + size + " OFFSET " + from;
        List<Map<String, Object>> rows = SqlUtil.query(dataSql, queryParams);

        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>(row);
            item.put("tags", parseJsonArray(row.get("tags")));
            item.put("categories", parseJsonArray(row.get("categories")));
            item.put("chapters", parseJsonArray(row.get("chapters")));

            // del_flag 转为 boolean
            Object df = row.get("del_flag");
            item.put("del_flag", df instanceof Number && ((Number) df).intValue() != 0);

            // 封面路径存入 Valkey
            String coverValkeyKey = row.get("id") + "cover";
            try {
                String coverPath = resolveFilePath(row, "cover.jpg");
                valkeyUtil.setEx(coverValkeyKey, coverPath, 3600);
            } catch (Exception ignored) {}
            item.put("cover_key", coverValkeyKey);

            items.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("total", total);
        return result;
    }

    // ======================== 软删除 ========================

    @PostMapping("/softDelete")
    public Map<String, Object> softDelete(@RequestBody JSONObject body) throws Exception {
        String id = body.getStr("id");
        String reason = body.getStr("reason", "");
        if (id == null) return Map.of("success", false, "error", "缺少 id");

        SqlUtil.exec(
                "UPDATE manga_source SET del_flag = 1, del_reason = {?varchar|r?} WHERE id = {?varchar|id?}",
                Map.of("r", reason, "id", id));
        return Map.of("success", true);
    }

    // ======================== 恢复删除 ========================

    @PostMapping("/restore")
    public Map<String, Object> restore(@RequestBody JSONObject body) throws Exception {
        String id = body.getStr("id");
        if (id == null) return Map.of("success", false, "error", "缺少 id");

        SqlUtil.exec(
                "UPDATE manga_source SET del_flag = 0, del_reason = NULL WHERE id = {?varchar|id?}",
                Map.of("id", id));
        return Map.of("success", true);
    }

    // ======================== 标签/类别搜索（含已删除） ========================

    @PostMapping("/searchTags")
    public Map<String, Object> searchTags(@RequestBody JSONObject body) throws Exception {
        String name = body.getStr("name", "");
        String sql;
        Map<String, Object> params = new HashMap<>();
        if (name != null && !name.isBlank()) {
            sql = "SELECT DISTINCT jsonb_array_elements_text(tags) AS tag FROM manga_source "
                    + "WHERE tags IS NOT NULL AND jsonb_array_elements_text(tags) ILIKE {?varchar|n?} LIMIT 30";
            params.put("n", "%" + name + "%");
        } else {
            sql = "SELECT DISTINCT jsonb_array_elements_text(tags) AS tag FROM manga_source "
                    + "WHERE tags IS NOT NULL LIMIT 50";
        }
        List<Map<String, Object>> rows = SqlUtil.query(sql, params);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            items.add(Map.of("tag", row.get("tag")));
        }
        return Map.of("success", true, "items", items, "total", items.size());
    }

    @PostMapping("/searchCategories")
    public Map<String, Object> searchCategories(@RequestBody JSONObject body) throws Exception {
        String name = body.getStr("name", "");
        String sql;
        Map<String, Object> params = new HashMap<>();
        if (name != null && !name.isBlank()) {
            sql = "SELECT DISTINCT jsonb_array_elements_text(categories) AS category FROM manga_source "
                    + "WHERE categories IS NOT NULL AND jsonb_array_elements_text(categories) ILIKE {?varchar|n?} LIMIT 30";
            params.put("n", "%" + name + "%");
        } else {
            sql = "SELECT DISTINCT jsonb_array_elements_text(categories) AS category FROM manga_source "
                    + "WHERE categories IS NOT NULL LIMIT 50";
        }
        List<Map<String, Object>> rows = SqlUtil.query(sql, params);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            items.add(Map.of("category", row.get("category")));
        }
        return Map.of("success", true, "items", items, "total", items.size());
    }

    // ======================== 封面/章节图片（复用逻辑） ========================

    @GetMapping("/cover")
    public void cover(@RequestParam String key,
                      jakarta.servlet.http.HttpServletResponse response) throws Exception {
        String filePath = valkeyUtil.get(key);
        if (filePath == null || filePath.isBlank()) { response.setStatus(404); return; }
        serveFile(filePath, response);
    }

    @GetMapping("/pageImage")
    public void pageImage(@RequestParam String key,
                          jakarta.servlet.http.HttpServletResponse response) throws Exception {
        String filePath = valkeyUtil.get(key);
        if (filePath == null || filePath.isBlank()) { response.setStatus(404); return; }
        serveFile(filePath, response);
    }

    @PostMapping("/chapterImages")
    public Map<String, Object> chapterImages(@RequestBody JSONObject body) throws Exception {
        String comicId = body.getStr("comicId");
        int chapterIndex = body.getInt("chapterIndex", 1);

        Map<String, Object> comic = SqlUtil.row(
                "SELECT type, path, title, directory, chapters FROM manga_source WHERE id = {?varchar|id?}",
                Map.of("id", comicId));
        if (comic == null) return Map.of("error", "漫画不存在");

        String chapterName = String.valueOf(chapterIndex);
        List<Object> chapters = parseJsonArray(comic.get("chapters"));
        for (Object ch : chapters) {
            if (ch instanceof Map) {
                Map<?, ?> cm = (Map<?, ?>) ch;
                Object idx = cm.get("index");
                if (idx != null && Integer.parseInt(idx.toString()) == chapterIndex) {
                    chapterName = cm.get("name") != null ? cm.get("name").toString() : chapterName;
                    break;
                }
            }
        }

        String dirPath = resolveFilePath(comic, String.valueOf(chapterIndex));
        java.io.File dir = new java.io.File(dirPath);
        List<String> imageKeys = new ArrayList<>();

        if (dir.isDirectory()) {
            java.io.File[] files = dir.listFiles((d, n) -> n.matches("\\d+\\.(jpg|jpeg|png|webp)"));
            if (files != null && files.length > 0) {
                Arrays.sort(files, Comparator.comparingInt(f -> {
                    String name = f.getName().replaceAll("[^0-9]", "");
                    return name.isEmpty() ? 0 : Integer.parseInt(name);
                }));
                for (java.io.File f : files) {
                    String nameNoExt = f.getName().replaceFirst("\\.[^.]+$", "");
                    String valkeyKey = comicId + "img" + nameNoExt;
                    valkeyUtil.setEx(valkeyKey, f.getAbsolutePath(), 3600);
                    imageKeys.add(valkeyKey);
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("chapterName", chapterName);
        result.put("chapterIndex", chapterIndex);
        result.put("comicId", comicId);
        result.put("imageKeys", imageKeys);
        return result;
    }

    // ======================== 内部工具 ========================

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "time", "title", "subtitle", "create_date", "pages_count", "likes"
    );

    private String buildOrderBy(String sortField, String sortOrder) {
        if (sortField == null || !ALLOWED_SORT_FIELDS.contains(sortField)) {
            return "ORDER BY time DESC";
        }
        boolean isText = "title".equals(sortField) || "subtitle".equals(sortField);
        String dir;
        if (sortOrder != null && !sortOrder.isBlank()) {
            dir = "asc".equalsIgnoreCase(sortOrder) ? "ASC" : "DESC";
        } else {
            dir = isText ? "ASC" : "DESC";
        }
        return "ORDER BY " + sortField + " " + dir + ", time DESC";
    }

    private String resolveFilePath(Map<String, Object> row, String suffix) {
        String type = (String) row.get("type");
        String path = (String) row.get("path");
        String directory = (String) row.get("directory");
        String typeDir = TYPE_DIR.getOrDefault(type, type);

        if (directory != null && directory.endsWith(".")) {
            if (path != null && path.compareTo("20251010") >= 0) {
                directory = directory.replaceAll("\\.+$", "");
            } else {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\.+$").matcher(directory);
                if (m.find()) {
                    directory = directory.substring(0, m.start()) + "_".repeat(m.group().length());
                }
            }
        }

        return systemConfig.getMediaRootPath() + "/" + typeDir + "/" + path + "/" + directory + "/" + suffix;
    }

    private void serveFile(String filePath, jakarta.servlet.http.HttpServletResponse response) throws Exception {
        java.io.File file = new java.io.File(filePath);
        if (!file.exists() || !file.isFile()) {
            response.setStatus(404);
            return;
        }
        String contentType = java.nio.file.Files.probeContentType(file.toPath());
        if (contentType == null) contentType = "application/octet-stream";
        response.setContentType(contentType);
        response.setContentLengthLong(file.length());

        try (java.io.InputStream in = new java.io.FileInputStream(file);
             java.io.OutputStream out = response.getOutputStream()) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
        }
    }

    private List<Object> parseJsonArray(Object value) {
        if (value == null) return List.of();
        if (value instanceof List) return (List<Object>) value;
        String str = value.toString();
        if (str.isBlank() || "null".equals(str)) return List.of();
        try {
            JSONArray arr = JSONUtil.parseArray(str);
            List<Object> result = new ArrayList<>();
            for (int i = 0; i < arr.size(); i++) {
                result.add(arr.get(i));
            }
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }
}
