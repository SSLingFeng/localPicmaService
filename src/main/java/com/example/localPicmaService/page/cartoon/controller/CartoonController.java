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
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/page/cartoon/api")
public class CartoonController {

    @Autowired
    private SystemConfig systemConfig;

    @Autowired
    private ValkeyUtil valkeyUtil;

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
        String sortField = params != null ? params.getStr("sortField", "") : "";
        String sortOrder = params != null ? params.getStr("sortOrder", "desc") : "desc";

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

        // 构建排序子句：白名单校验防注入
        String orderBy = buildOrderBy(sortField, sortOrder);

        String dataSql = "SELECT id, type, title, author, chinese_team, description, "
                + "tags, categories, pages_count, chapters, likes, comments, time, path, directory "
                + "FROM manga_source WHERE " + where + " " + orderBy + " LIMIT " + size + " OFFSET " + from;
        List<Map<String, Object>> rows = SqlUtil.query(dataSql, queryParams);

        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>(row);
            item.put("tags", parseJsonArray(row.get("tags")));
            item.put("categories", parseJsonArray(row.get("categories")));
            item.put("chapters", parseJsonArray(row.get("chapters")));

            // 封面路径存入 Valkey，返回 cover_key
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

    // ======================== 封面图片（Valkey key 查询） ========================

    @GetMapping("/cover")
    public void cover(@RequestParam String key,
                      jakarta.servlet.http.HttpServletResponse response) throws Exception {
        String filePath = valkeyUtil.get(key);
        if (filePath == null || filePath.isBlank()) { response.setStatus(404); return; }
        serveFile(filePath, response);
    }

    // ======================== 章节图片（Valkey key 查询） ========================

    @GetMapping("/pageImage")
    public void pageImage(@RequestParam String key,
                          jakarta.servlet.http.HttpServletResponse response) throws Exception {
        String filePath = valkeyUtil.get(key);
        if (filePath == null || filePath.isBlank()) { response.setStatus(404); return; }
        serveFile(filePath, response);
    }

    // ======================== 章节图片列表（返回 Valkey key 数组） ========================

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

        // 扫描章节目录，生成 Valkey key 数组
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
                    // key = comicId + "img" + 文件名去掉后缀
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

    /** 排序字段白名单 */
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "time", "title", "subtitle", "create_date", "pages_count", "likes"
    );

    /**
     * 构建 ORDER BY 子句
     * sortField 为空或不在白名单内时，默认 time DESC
     * 若 sortField 有效，则 sortField 排在前面，time DESC 作为次要排序
     * 文本字段默认 ASC，数值/时间字段默认 DESC
     */
    private String buildOrderBy(String sortField, String sortOrder) {
        if (sortField == null || !ALLOWED_SORT_FIELDS.contains(sortField)) {
            return "ORDER BY time DESC";
        }
        // 文本字段默认升序，其余降序
        boolean isText = "title".equals(sortField) || "subtitle".equals(sortField);
        String dir;
        if (sortOrder != null && !sortOrder.isBlank()) {
            dir = "asc".equalsIgnoreCase(sortOrder) ? "ASC" : "DESC";
        } else {
            dir = isText ? "ASC" : "DESC";
        }
        return "ORDER BY " + sortField + " " + dir + ", time DESC";
    }

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
