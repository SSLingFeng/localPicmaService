package com.example.localPicmaService.page.cartoon.controller;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.example.localPicmaService.config.SystemConfig;
import com.example.localPicmaService.tool.SQLTool.SqlUtil;
import com.example.localPicmaService.tool.Valkey.ValkeyUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

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

    /** 带过期时间戳的图片缓存值 */
    private static class CacheEntry {
        final byte[] data;
        final long expireAt; // System.currentTimeMillis() + ttl
        CacheEntry(byte[] data, long expireAt) {
            this.data = data;
            this.expireAt = expireAt;
        }
    }

    /** 章节图片缓存：支持 per-entry 动态 TTL */
    private static final Cache<String, CacheEntry> IMAGE_CACHE = Caffeine.newBuilder()
            .maximumSize(4000)
            .expireAfter(new com.github.benmanes.caffeine.cache.Expiry<String, CacheEntry>() {
                @Override
                public long expireAfterCreate(String key, CacheEntry entry, long currentTime) {
                    long ttlNanos = (entry.expireAt - System.currentTimeMillis()) * 1_000_000;
                    return Math.max(ttlNanos, 1);
                }
                @Override
                public long expireAfterUpdate(String key, CacheEntry entry, long currentTime, long currentDuration) {
                    return currentDuration;
                }
                @Override
                public long expireAfterRead(String key, CacheEntry entry, long currentTime, long currentDuration) {
                    return currentDuration;
                }
            })
            .build();

    /** 根据章节特征计算缓存过期时间（秒） */
    private long calcTtlSeconds(int imageCount, long zipSizeBytes) {
        if (imageCount > 600 || zipSizeBytes > 600L * 1024 * 1024) {
            return 15 * 60;  // 15 分钟
        }
        if (imageCount > 300 || zipSizeBytes > 300L * 1024 * 1024) {
            return 30 * 60;  // 30 分钟
        }
        return 60 * 60;      // 默认 1 小时
    }

    // ======================== 获取当前用户 ========================

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName();
        }
        return null;
    }

    /** 根据用户名查询 web_user.id，未找到返回 null */
    private String getCurrentUserId() {
        String username = getCurrentUsername();
        if (username == null) return null;
        try {
            Map<String, Object> row = SqlUtil.row(
                    "SELECT id FROM web_user WHERE user_name = {?varchar|u?}", Map.of("u", username));
            return row != null ? row.get("id").toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

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
        String prefFilter = params != null ? params.getStr("prefFilter", "") : "";

        String currentUser = getCurrentUserId();

        List<String> conditions = new ArrayList<>();
        Map<String, Object> queryParams = new LinkedHashMap<>();
        int paramIdx = 0;

        conditions.add("ms.del_flag = 0");

        // 收藏/厌恶过滤
        if ("favorite".equals(prefFilter)) {
            if (currentUser != null) {
                conditions.add("EXISTS (SELECT 1 FROM manga_user_preference p "
                        + "WHERE p.picg_id = ms.picg_id AND p.user_id = {?varchar|pfu?} AND p.pref_type = 1 AND p.del_flag = 0)");
                queryParams.put("pfu", currentUser);
            } else {
                return Map.of("items", List.of(), "total", 0);
            }
        } else if ("dislike".equals(prefFilter)) {
            if (currentUser != null) {
                conditions.add("EXISTS (SELECT 1 FROM manga_user_preference p "
                        + "WHERE p.picg_id = ms.picg_id AND p.user_id = {?varchar|pdu?} AND p.pref_type = -1 AND p.del_flag = 0)");
                queryParams.put("pdu", currentUser);
            } else {
                return Map.of("items", List.of(), "total", 0);
            }
        } else {
            if (currentUser != null) {
                conditions.add("NOT EXISTS (SELECT 1 FROM manga_user_preference p "
                        + "WHERE p.picg_id = ms.picg_id AND p.user_id = {?varchar|pdu?} AND p.pref_type = -1 AND p.del_flag = 0)");
                queryParams.put("pdu", currentUser);
            }
        }

        if (searchTitle != null && !searchTitle.isBlank()) {
            conditions.add("ms.title ILIKE {?varchar|p" + paramIdx + "?}");
            queryParams.put("p" + paramIdx, "%" + searchTitle + "%");
            paramIdx++;
        }
        if (searchType != null && !searchType.isBlank()) {
            conditions.add("ms.type = {?varchar|p" + paramIdx + "?}");
            queryParams.put("p" + paramIdx, searchType);
            paramIdx++;
        }
        for (String tag : searchTags) {
            conditions.add("ms.tags::jsonb @> {?varchar|p" + paramIdx + "?}::jsonb");
            queryParams.put("p" + paramIdx, "[\"" + tag + "\"]");
            paramIdx++;
        }
        for (String cat : searchCats) {
            conditions.add("ms.categories::jsonb @> {?varchar|p" + paramIdx + "?}::jsonb");
            queryParams.put("p" + paramIdx, "[\"" + cat + "\"]");
            paramIdx++;
        }

        String where = String.join(" AND ", conditions);

        String countSql = "SELECT COUNT(*) AS cnt FROM manga_source ms WHERE " + where;
        Map<String, Object> countRow = SqlUtil.row(countSql, queryParams);
        long total = 0;
        if (countRow != null) {
            Object cnt = countRow.values().iterator().next();
            if (cnt instanceof Number) total = ((Number) cnt).longValue();
        }

        String orderBy = buildOrderBy(sortField, sortOrder);

        // 查询数据，附带当前用户的收藏状态
        String favSelect = currentUser != null
                ? ", (SELECT p.pref_type FROM manga_user_preference p WHERE p.picg_id = ms.picg_id AND p.user_id = {?varchar|favu?} AND p.del_flag = 0) AS my_pref"
                : ", NULL AS my_pref";
        if (currentUser != null) queryParams.put("favu", currentUser);

        String dataSql = "SELECT ms.id, ms.type, ms.picg_id, ms.title, ms.author, ms.chinese_team, "
                + "ms.tags, ms.categories, ms.pages_count, ms.time, ms.path, ms.directory"
                + favSelect
                + " FROM manga_source ms WHERE " + where + " " + orderBy + " LIMIT " + size + " OFFSET " + from;
        List<Map<String, Object>> rows = SqlUtil.query(dataSql, queryParams);

        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>(row);
            item.put("tags", parseJsonArray(row.get("tags")));
            item.put("categories", parseJsonArray(row.get("categories")));

            // 收藏状态
            Object myPref = row.get("my_pref");
            item.put("favorited", myPref instanceof Number && ((Number) myPref).intValue() == 1);
            item.put("disliked", myPref instanceof Number && ((Number) myPref).intValue() == -1);
            item.remove("my_pref");

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

    // ======================== 随机推荐 ========================

    @GetMapping("/recommend")
    public Map<String, Object> recommend() throws Exception {
        String currentUser = getCurrentUserId();
        Map<String, Object> params = new LinkedHashMap<>();
        if (currentUser != null) params.put("uid", currentUser);

        String excludeClause = currentUser != null
                ? " AND NOT EXISTS (SELECT 1 FROM manga_user_preference dp WHERE dp.picg_id = ms.picg_id AND dp.user_id = {?varchar|uid?} AND dp.del_flag = 0)"
                : "";

        List<Map<String, Object>> rows = SqlUtil.query(
                "SELECT ms.id, ms.type, ms.picg_id, ms.title, ms.author, ms.chinese_team, "
                        + "ms.tags, ms.categories, ms.pages_count, ms.time, ms.path, ms.directory"
                        + " FROM manga_source ms"
                        + " WHERE ms.del_flag = 0" + excludeClause
                        + " ORDER BY RANDOM() LIMIT 20",
                params, 20);

        return buildCardList(rows);
    }

    // ======================== 收藏列表 ========================

    @GetMapping("/favorites")
    public Map<String, Object> favorites() throws Exception {
        String currentUser = getCurrentUserId();
        if (currentUser == null) return Map.of("items", List.of(), "total", 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("uid", currentUser);

        List<Map<String, Object>> rows = SqlUtil.query(
                "SELECT ms.id, ms.type, ms.picg_id, ms.title, ms.author, ms.chinese_team, "
                        + "ms.tags, ms.categories, ms.pages_count, ms.time, ms.path, ms.directory, 1 AS my_pref"
                        + " FROM manga_user_preference p"
                        + " JOIN manga_source ms ON ms.picg_id = p.picg_id AND ms.del_flag = 0"
                        + " WHERE p.user_id = {?varchar|uid?} AND p.pref_type = 1 AND p.del_flag = 0"
                        + " ORDER BY p.create_date DESC",
                params, 200);

        return buildCardList(rows);
    }

    /** 统一构建卡片列表（推荐/收藏共用） */
    private Map<String, Object> buildCardList(List<Map<String, Object>> rows) {
        List<Map<String, Object>> items = new ArrayList<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                Map<String, Object> item = new LinkedHashMap<>(row);
                item.put("tags", parseJsonArray(row.get("tags")));
                item.put("categories", parseJsonArray(row.get("categories")));
                Object myPref = row.get("my_pref");
                item.put("favorited", myPref instanceof Number && ((Number) myPref).intValue() == 1);
                item.put("disliked", myPref instanceof Number && ((Number) myPref).intValue() == -1);
                item.remove("my_pref");
                String coverValkeyKey = row.get("id") + "cover";
                try {
                    String coverPath = resolveFilePath(row, "cover.jpg");
                    valkeyUtil.setEx(coverValkeyKey, coverPath, 3600);
                } catch (Exception ignored) {}
                item.put("cover_key", coverValkeyKey);
                items.add(item);
            }
        }
        return Map.of("items", items, "total", items.size());
    }

    // ======================== 漫画详情（统一接口） ========================

    @GetMapping("/detail")
    public Map<String, Object> detail(@RequestParam String id) throws Exception {
        String currentUser = getCurrentUserId();
        String favSelect = currentUser != null
                ? ", (SELECT p.pref_type FROM manga_user_preference p WHERE p.picg_id = ms.picg_id AND p.user_id = {?varchar|uid?} AND p.del_flag = 0) AS my_pref"
                : ", NULL AS my_pref";
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", id);
        if (currentUser != null) params.put("uid", currentUser);

        Map<String, Object> row = SqlUtil.row(
                "SELECT ms.*" + favSelect
                        + " FROM manga_source ms WHERE ms.id = {?varchar|id?} AND ms.del_flag = 0",
                params);

        if (row == null) return Map.of("success", false, "error", "漫画不存在");

        Map<String, Object> item = new LinkedHashMap<>(row);
        item.put("tags", parseJsonArray(row.get("tags")));
        item.put("categories", parseJsonArray(row.get("categories")));
        item.put("chapters", parseJsonArray(row.get("chapters")));
        item.put("creator", parseJsonObj(row.get("creator")));
        Object myPref = row.get("my_pref");
        item.put("favorited", myPref instanceof Number && ((Number) myPref).intValue() == 1);
        item.put("disliked", myPref instanceof Number && ((Number) myPref).intValue() == -1);
        item.remove("my_pref");

        // 封面
        String coverValkeyKey = id + "cover";
        try {
            String coverPath = resolveFilePath(row, "cover.jpg");
            valkeyUtil.setEx(coverValkeyKey, coverPath, 3600);
        } catch (Exception ignored) {}
        item.put("cover_key", coverValkeyKey);

        return Map.of("success", true, "data", item);
    }

    // ======================== 收藏/厌恶 切换（基于 picg_id） ========================

    @PostMapping("/toggleFavorite")
    public Map<String, Object> toggleFavorite(@RequestBody JSONObject body) throws Exception {
        String userId = getCurrentUserId();
        if (userId == null) return Map.of("success", false, "error", "未登录");

        String picgId = body.getStr("picgId");
        if (picgId == null) return Map.of("success", false, "error", "缺少 picgId");

        Map<String, Object> existing = SqlUtil.row(
                "SELECT id, pref_type FROM manga_user_preference WHERE user_id = {?varchar|u?} AND picg_id = {?varchar|p?} AND del_flag = 0",
                Map.of("u", userId, "p", picgId));

        if (existing != null) {
            int type = ((Number) existing.get("pref_type")).intValue();
            if (type == 1) {
                SqlUtil.exec("UPDATE manga_user_preference SET del_flag = 1, update_date = NOW() WHERE id = {?varchar|id?}", Map.of("id", existing.get("id")));
                return Map.of("success", true, "action", "unfavorite");
            } else {
                SqlUtil.exec("UPDATE manga_user_preference SET pref_type = 1, del_flag = 0, update_date = NOW() WHERE id = {?varchar|id?}",
                        Map.of("id", existing.get("id")));
                return Map.of("success", true, "action", "favorite");
            }
        } else {
            String id = UUID.randomUUID().toString().replace("-", "").substring(0, 32);
            SqlUtil.exec("INSERT INTO manga_user_preference (id, user_id, picg_id, pref_type, create_date) "
                            + "VALUES ({?varchar|id?}, {?varchar|u?}, {?varchar|p?}, 1, NOW())",
                    Map.of("id", id, "u", userId, "p", picgId));
            return Map.of("success", true, "action", "favorite");
        }
    }

    @PostMapping("/toggleDislike")
    public Map<String, Object> toggleDislike(@RequestBody JSONObject body) throws Exception {
        String userId = getCurrentUserId();
        if (userId == null) return Map.of("success", false, "error", "未登录");

        String picgId = body.getStr("picgId");
        if (picgId == null) return Map.of("success", false, "error", "缺少 picgId");

        Map<String, Object> existing = SqlUtil.row(
                "SELECT id, pref_type FROM manga_user_preference WHERE user_id = {?varchar|u?} AND picg_id = {?varchar|p?} AND del_flag = 0",
                Map.of("u", userId, "p", picgId));

        if (existing != null) {
            int type = ((Number) existing.get("pref_type")).intValue();
            if (type == -1) {
                SqlUtil.exec("UPDATE manga_user_preference SET del_flag = 1, update_date = NOW() WHERE id = {?varchar|id?}", Map.of("id", existing.get("id")));
                return Map.of("success", true, "action", "undislike");
            } else {
                SqlUtil.exec("UPDATE manga_user_preference SET pref_type = -1, del_flag = 0, update_date = NOW() WHERE id = {?varchar|id?}",
                        Map.of("id", existing.get("id")));
                return Map.of("success", true, "action", "dislike");
            }
        } else {
            String id = UUID.randomUUID().toString().replace("-", "").substring(0, 32);
            SqlUtil.exec("INSERT INTO manga_user_preference (id, user_id, picg_id, pref_type, create_date) "
                            + "VALUES ({?varchar|id?}, {?varchar|u?}, {?varchar|p?}, -1, NOW())",
                    Map.of("id", id, "u", userId, "p", picgId));
            return Map.of("success", true, "action", "dislike");
        }
    }

    // ======================== 封面图片（Valkey key 查询） ========================

    @GetMapping("/cover")
    public void cover(@RequestParam String key,
                      jakarta.servlet.http.HttpServletResponse response) throws Exception {
        String filePath = valkeyUtil.get(key);
        if (filePath == null || filePath.isBlank()) { response.setStatus(404); return; }
        serveFile(filePath, response);
    }

    // ======================== 章节图片（Valkey 缓存 + zip 读取） ========================

    @GetMapping("/pageImage")
    public void pageImage(@RequestParam String key,
                          jakarta.servlet.http.HttpServletResponse response) throws Exception {
        // 1. 尝试从 Caffeine 缓存获取图片字节
        CacheEntry cached = IMAGE_CACHE.getIfPresent(key);
        if (cached != null) {
            String contentType = detectContentType(key);
            response.setContentType(contentType);
            response.setContentLength(cached.data.length);
            response.getOutputStream().write(cached.data);
            return;
        }

        // 2. 缓存未命中，从 Valkey 元数据获取来源
        String meta = valkeyUtil.get(key + ":meta");
        if (meta == null || meta.isBlank()) { response.setStatus(404); return; }

        byte[] imageData;
        String contentType;
        long ttlSeconds = 3600; // 默认 1 小时

        if (meta.startsWith("file:")) {
            // 文件路径模式（未压缩的章节）
            String filePath = meta.substring(5);
            java.io.File file = new java.io.File(filePath);
            if (!file.exists()) { response.setStatus(404); return; }
            imageData = java.nio.file.Files.readAllBytes(file.toPath());
            contentType = detectContentType(filePath);
        } else {
            // zip 模式：zipPath|entryName|imageCount|zipSizeBytes
            String[] parts = meta.split("\\|", 4);
            if (parts.length < 2) { response.setStatus(404); return; }
            String zipPath = parts[0];
            String entryName = parts[1];
            if (parts.length >= 4) {
                int imgCount = Integer.parseInt(parts[2]);
                long zipSize = Long.parseLong(parts[3]);
                ttlSeconds = calcTtlSeconds(imgCount, zipSize);
            }
            imageData = readZipEntry(zipPath, entryName);
            if (imageData == null) { response.setStatus(404); return; }
            contentType = detectContentType(entryName);
        }

        // 3. 缓存到 Caffeine（动态 TTL）
        long expireAt = System.currentTimeMillis() + ttlSeconds * 1000;
        IMAGE_CACHE.put(key, new CacheEntry(imageData, expireAt));

        // 4. 返回
        response.setContentType(contentType);
        response.setContentLength(imageData.length);
        response.getOutputStream().write(imageData);
    }

    // ======================== 章节图片列表（从 zip 扫描，返回 Valkey key 数组） ========================

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

        String chapterDir = resolveFilePath(comic, String.valueOf(chapterIndex));
        String zipPath = chapterDir + ".zip";
        List<String> imageKeys = new ArrayList<>();

        java.io.File zipFile = new java.io.File(zipPath);
        if (zipFile.exists()) {
            // 从 zip 扫描图片条目
            List<String> entries = listZipImageEntries(zipPath);
            long zipSize = zipFile.length();
            int imgCount = entries.size();
            for (int i = 0; i < entries.size(); i++) {
                String entryName = entries.get(i);
                String nameNoExt = entryName.replaceFirst("\\.[^.]+$", "");
                String valkeyKey = comicId + "img" + nameNoExt;
                // 元数据：zip路径|entry名称|图片数|zip字节大小
                valkeyUtil.setEx(valkeyKey + ":meta",
                        zipPath + "|" + entryName + "|" + imgCount + "|" + zipSize, 3600);
                imageKeys.add(valkeyKey);
            }
        } else {
            // zip 不存在，尝试从目录读取（兼容未压缩的情况）
            java.io.File dir = new java.io.File(chapterDir);
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
                        // 直接读文件，标记为文件路径
                        valkeyUtil.setEx(valkeyKey + ":meta", "file:" + f.getAbsolutePath(), 3600);
                        imageKeys.add(valkeyKey);
                    }
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

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "time", "title", "subtitle", "create_date", "pages_count", "likes"
    );

    private String buildOrderBy(String sortField, String sortOrder) {
        if (sortField == null || !ALLOWED_SORT_FIELDS.contains(sortField)) {
            return "ORDER BY ms.time DESC";
        }
        boolean isText = "title".equals(sortField) || "subtitle".equals(sortField);
        String dir;
        if (sortOrder != null && !sortOrder.isBlank()) {
            dir = "asc".equalsIgnoreCase(sortOrder) ? "ASC" : "DESC";
        } else {
            dir = isText ? "ASC" : "DESC";
        }
        return "ORDER BY ms." + sortField + " " + dir + ", ms.time DESC";
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

    private Map<String, Object> parseJsonObj(Object value) {
        if (value == null) return Map.of();
        if (value instanceof Map) return (Map<String, Object>) value;
        String str = value.toString();
        if (str.isBlank() || "null".equals(str)) return Map.of();
        try {
            return JSONUtil.parseObj(str).toBean(Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    // ======================== Zip 工具方法 ========================

    /** 扫描 zip 中的图片条目，按文件名数字排序 */
    private List<String> listZipImageEntries(String zipPath) {
        List<String> entries = new ArrayList<>();
        try (ZipFile zip = new ZipFile(zipPath)) {
            Enumeration<? extends ZipEntry> en = zip.entries();
            while (en.hasMoreElements()) {
                ZipEntry entry = en.nextElement();
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                // 只取文件名部分（忽略子目录）
                int lastSlash = name.lastIndexOf('/');
                if (lastSlash >= 0) name = name.substring(lastSlash + 1);
                if (name.matches("\\d+\\.(jpg|jpeg|png|webp)")) {
                    entries.add(name);
                }
            }
        } catch (Exception ignored) {}
        // 按文件名数字排序
        entries.sort(Comparator.comparingInt(n -> {
            String num = n.replaceAll("[^0-9]", "");
            return num.isEmpty() ? 0 : Integer.parseInt(num);
        }));
        return entries;
    }

    /** 从 zip 中读取指定条目的字节数据 */
    private byte[] readZipEntry(String zipPath, String entryName) {
        try (ZipFile zip = new ZipFile(zipPath)) {
            ZipEntry entry = zip.getEntry(entryName);
            if (entry == null) {
                // 尝试不带路径前缀的匹配
                Enumeration<? extends ZipEntry> en = zip.entries();
                while (en.hasMoreElements()) {
                    ZipEntry e = en.nextElement();
                    if (e.getName().endsWith(entryName)) { entry = e; break; }
                }
            }
            if (entry == null) return null;
            try (InputStream is = zip.getInputStream(entry)) {
                return is.readAllBytes();
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** 根据文件名检测 Content-Type */
    private String detectContentType(String name) {
        if (name == null) return "image/jpeg";
        String lower = name.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".gif")) return "image/gif";
        return "image/jpeg";
    }
}
