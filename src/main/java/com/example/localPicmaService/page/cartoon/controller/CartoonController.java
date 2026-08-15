package com.example.localPicmaService.page.cartoon.controller;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.example.localPicmaService.config.SystemConfig;
import com.example.localPicmaService.tool.SQLTool.SqlUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/page/cartoon/api")
public class CartoonController {

    @Autowired
    private SystemConfig systemConfig;

    private static final Map<String, String> TYPE_DIR = Map.of(
            "漫画", "cartoon",
            "coser", "coser"
    );

    // ======================== 漫画列表 ========================

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

        List<String> conditions = new ArrayList<>();
        Map<String, Object> queryParams = new LinkedHashMap<>();
        int paramIdx = 0;

        conditions.add("del_flag = 0");

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

        String where = String.join(" AND ", conditions);

        String countSql = "SELECT COUNT(*) AS cnt FROM manga_source WHERE " + where;
        Map<String, Object> countRow = SqlUtil.row(countSql, queryParams);
        long total = 0;
        if (countRow != null) {
            Object cnt = countRow.values().iterator().next();
            if (cnt instanceof Number) total = ((Number) cnt).longValue();
        }

        String dataSql = "SELECT id, type, title, author, chinese_team, description, "
                + "tags, categories, pages_count, chapters, likes, comments, time "
                + "FROM manga_source WHERE " + where + " ORDER BY create_date DESC LIMIT " + size + " OFFSET " + from;
        List<Map<String, Object>> rows = SqlUtil.query(dataSql, queryParams);

        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>(row);
            item.put("tags", parseJsonArray(row.get("tags")));
            item.put("categories", parseJsonArray(row.get("categories")));
            item.put("chapters", parseJsonArray(row.get("chapters")));
            // 封面 URL 仅暴露 ID，不暴露路径
            item.put("cover_url", "/page/cartoon/api/cover?id=" + row.get("id"));
            items.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("total", total);
        return result;
    }

    // ======================== 封面图片（ID 查询） ========================

    @GetMapping("/cover")
    public void cover(@RequestParam String id,
                      jakarta.servlet.http.HttpServletResponse response) throws Exception {
        Map<String, Object> row = SqlUtil.row(
                "SELECT type, path, title, directory FROM manga_source WHERE id = {?varchar|id?}",
                Map.of("id", id));
        if (row == null) { response.setStatus(404); return; }

        String filePath = resolveFilePath(row, "cover.jpg");
        serveFile(filePath, response);
    }

    // ======================== 章节图片（ID + 章节 + 页码） ========================

    @GetMapping("/pageImage")
    public void pageImage(@RequestParam String comicId,
                          @RequestParam int chapter,
                          @RequestParam int page,
                          jakarta.servlet.http.HttpServletResponse response) throws Exception {
        Map<String, Object> row = SqlUtil.row(
                "SELECT type, path, title, directory FROM manga_source WHERE id = {?varchar|id?}",
                Map.of("id", comicId));
        if (row == null) { response.setStatus(404); return; }

        String dirPath = resolveFilePath(row, String.valueOf(chapter));
        java.io.File dir = new java.io.File(dirPath);
        if (!dir.isDirectory()) { response.setStatus(404); return; }

        // 找到第 page 张图片（按文件名数字排序）
        java.io.File[] files = dir.listFiles((d, n) -> n.matches("\\d+\\.(jpg|jpeg|png|webp)"));
        if (files == null || files.length == 0) { response.setStatus(404); return; }

        Arrays.sort(files, Comparator.comparingInt(f -> {
            String name = f.getName().replaceAll("[^0-9]", "");
            return name.isEmpty() ? 0 : Integer.parseInt(name);
        }));

        if (page < 1 || page > files.length) { response.setStatus(404); return; }
        serveFile(files[page - 1].getAbsolutePath(), response);
    }

    // ======================== 章节图片列表（返回页码数，不含路径） ========================

    @PostMapping("/chapterImages")
    public Map<String, Object> chapterImages(@RequestBody JSONObject body) throws Exception {
        String comicId = body.getStr("comicId");
        int chapterIndex = body.getInt("chapterIndex", 1);

        Map<String, Object> comic = SqlUtil.row(
                "SELECT type, path, title, directory, chapters FROM manga_source WHERE id = {?varchar|id?}",
                Map.of("id", comicId));
        if (comic == null) return Map.of("error", "漫画不存在");

        // 章节名称
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

        // 扫描章节目录，返回图片数量
        String dirPath = resolveFilePath(comic, String.valueOf(chapterIndex));
        java.io.File dir = new java.io.File(dirPath);
        int imageCount = 0;
        if (dir.isDirectory()) {
            java.io.File[] files = dir.listFiles((d, n) -> n.matches("\\d+\\.(jpg|jpeg|png|webp)"));
            if (files != null) imageCount = files.length;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("chapterName", chapterName);
        result.put("chapterIndex", chapterIndex);
        result.put("comicId", comicId);
        result.put("imageCount", imageCount);
        return result;
    }

    // ======================== 标签搜索 ========================

    @PostMapping("/searchTags")
    public Map<String, Object> searchTags(@RequestBody JSONObject body) throws Exception {
        String name = body.getStr("name", "");
        String sql;
        Map<String, Object> params = new HashMap<>();
        if (name != null && !name.isBlank()) {
            sql = "SELECT DISTINCT jsonb_array_elements_text(tags) AS tag FROM manga_source "
                    + "WHERE del_flag = 0 AND tags IS NOT NULL AND jsonb_array_elements_text(tags) ILIKE {?varchar|n?} LIMIT 30";
            params.put("n", "%" + name + "%");
        } else {
            sql = "SELECT DISTINCT jsonb_array_elements_text(tags) AS tag FROM manga_source "
                    + "WHERE del_flag = 0 AND tags IS NOT NULL LIMIT 50";
        }
        List<Map<String, Object>> rows = SqlUtil.query(sql, params);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            items.add(Map.of("tag", row.get("tag")));
        }
        return Map.of("success", true, "items", items, "total", items.size());
    }

    // ======================== 类别搜索 ========================

    @PostMapping("/searchCategories")
    public Map<String, Object> searchCategories(@RequestBody JSONObject body) throws Exception {
        String name = body.getStr("name", "");
        String sql;
        Map<String, Object> params = new HashMap<>();
        if (name != null && !name.isBlank()) {
            sql = "SELECT DISTINCT jsonb_array_elements_text(categories) AS category FROM manga_source "
                    + "WHERE del_flag = 0 AND categories IS NOT NULL AND jsonb_array_elements_text(categories) ILIKE {?varchar|n?} LIMIT 30";
            params.put("n", "%" + name + "%");
        } else {
            sql = "SELECT DISTINCT jsonb_array_elements_text(categories) AS category FROM manga_source "
                    + "WHERE del_flag = 0 AND categories IS NOT NULL LIMIT 50";
        }
        List<Map<String, Object>> rows = SqlUtil.query(sql, params);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            items.add(Map.of("category", row.get("category")));
        }
        return Map.of("success", true, "items", items, "total", items.size());
    }

    // ======================== 内部工具 ========================

    /**
     * 根据数据库记录拼接文件路径
     * 格式: {mediaRootPath}/{typeDir}/{path}/{directory}/{suffix}
     *
     * 适配历史遗留: Windows 目录名末尾的 "." 会被强制替换为 "_",
     * 需要根据 path 日期判断修正方式:
     *   path >= "20251010" → 去掉末尾点号
     *   path <  "20251010" → 末尾点号替换为下划线
     */
    private String resolveFilePath(Map<String, Object> row, String suffix) {
        String type = (String) row.get("type");
        String path = (String) row.get("path");
        String directory = (String) row.get("directory");
        String typeDir = TYPE_DIR.getOrDefault(type, type);

        // 先修正 directory 末尾的点号（Windows 目录名末尾 "." 会被强制替换为 "_"）
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

    /** 读取本地文件并写入响应 */
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
