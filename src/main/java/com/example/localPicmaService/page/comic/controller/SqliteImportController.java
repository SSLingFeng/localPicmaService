package com.example.localPicmaService.page.comic.controller;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.example.localPicmaService.config.SystemConfig;
import com.example.localPicmaService.tool.SQLTool.SqlUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * SQLite 数据导入 + 自动章节压缩
 * 基于 DataControl 的逻辑，支持上传 .db 文件
 */
@RestController
@RequestMapping("/page/comic/import/api")
public class SqliteImportController {

    private static final Logger log = LoggerFactory.getLogger(SqliteImportController.class);

    @Autowired
    private SystemConfig systemConfig;

    private static final Map<String, String> TYPE_DIR = Map.of(
            "漫画", "cartoon",
            "coser", "coser"
    );

    // ======================== 任务状态 ========================

    private static volatile boolean running = false;
    private static volatile String currentStatus = "空闲";
    private static volatile int totalItems = 0;
    private static volatile int processedItems = 0;
    private static volatile int skippedItems = 0;
    private static volatile int totalChapters = 0;
    private static volatile int zippedChapters = 0;
    private static volatile int deletedFolders = 0;
    private static final List<String> logs = Collections.synchronizedList(new ArrayList<>());

    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("running", running);
        r.put("status", currentStatus);
        r.put("totalItems", totalItems);
        r.put("processedItems", processedItems);
        r.put("skippedItems", skippedItems);
        r.put("totalChapters", totalChapters);
        r.put("zippedChapters", zippedChapters);
        r.put("deletedFolders", deletedFolders);
        synchronized (logs) {
            int from = Math.max(0, logs.size() - 100);
            r.put("logs", new ArrayList<>(logs.subList(from, logs.size())));
        }
        return r;
    }

    @PostMapping("/stop")
    public Map<String, Object> stop() {
        if (!running) return Map.of("success", false, "error", "没有运行中的任务");
        running = false;
        addLog("正在停止...");
        return Map.of("success", true);
    }

    // ======================== 上传并导入 ========================

    @PostMapping("/upload")
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file,
                                      @RequestParam("type") String type,
                                      @RequestParam("path") String path) {
        if (running) return Map.of("success", false, "error", "任务正在运行中");
        if (file.isEmpty()) return Map.of("success", false, "error", "文件为空");
        if (!file.getOriginalFilename().endsWith(".db"))
            return Map.of("success", false, "error", "仅支持 .db 文件");
        if (type == null || type.isBlank())
            return Map.of("success", false, "error", "请选择类型");
        if (path == null || path.isBlank())
            return Map.of("success", false, "error", "请输入路径");

        // 保存到临时文件
        File tmpFile;
        try {
            tmpFile = File.createTempFile("sqlite_import_", ".db");
            file.transferTo(tmpFile);
        } catch (Exception e) {
            return Map.of("success", false, "error", "文件保存失败: " + e.getMessage());
        }

        // 重置状态
        running = true;
        currentStatus = "启动中...";
        logs.clear();
        totalItems = 0; processedItems = 0; skippedItems = 0;
        totalChapters = 0; zippedChapters = 0; deletedFolders = 0;

        new Thread(() -> runTask(tmpFile, type, path), "sqlite-import-worker").start();

        return Map.of("success", true, "message", "任务已启动");
    }

    // ======================== 核心逻辑 ========================

    private void runTask(File dbFile, String type, String path) {
        try {
            String mediaRoot = systemConfig.getMediaRootPath();
            if (mediaRoot == null || mediaRoot.isBlank()) {
                addLog("错误：mediaRootPath 未配置");
                currentStatus = "失败（配置缺失）";
                return;
            }
            String typeDir = TYPE_DIR.getOrDefault(type, type);

            // 1. 读取 SQLite download 表
            addLog("读取 SQLite 文件: " + dbFile.getName());
            List<Map<String, Object>> downloadRows = readSqliteDownload(dbFile);
            if (downloadRows.isEmpty()) {
                addLog("download 表为空，无数据可导入");
                currentStatus = "完成（无数据）";
                return;
            }
            totalItems = downloadRows.size();
            addLog("共读取 " + totalItems + " 条记录");

            // 2. 校验文件夹是否存在
            currentStatus = "校验文件夹...";
            addLog("开始校验文件夹...");
            List<Map<String, Object>> validRows = new ArrayList<>();
            for (Map<String, Object> row : downloadRows) {
                String directory = (String) row.get("directory");
                if (directory == null || directory.isBlank()) {
                    addLog("  跳过: " + row.get("title") + " (directory 为空)");
                    skippedItems++;
                    continue;
                }
                // 修正 directory 末尾点号
                directory = fixDirectory(directory, path);
                String comicDir = mediaRoot + "/" + typeDir + "/" + path + "/" + directory;

                File dir = new File(comicDir);
                if (!dir.exists() || !dir.isDirectory()) {
                    addLog("  跳过: " + row.get("title") + " (文件夹不存在: " + comicDir + ")");
                    skippedItems++;
                    continue;
                }

                // 检查是否有章节子目录
                File[] chapterDirs = dir.listFiles(File::isDirectory);
                if (chapterDirs == null || chapterDirs.length == 0) {
                    addLog("  跳过: " + row.get("title") + " (无章节子目录)");
                    skippedItems++;
                    continue;
                }

                validRows.add(row);
            }

            if (validRows.isEmpty()) {
                addLog("校验完成：无有效数据可导入");
                currentStatus = "完成（无有效数据）";
                return;
            }
            addLog("校验通过: " + validRows.size() + " 条，跳过: " + skippedItems + " 条");

            // 3. 转换并插入 manga_source
            currentStatus = "导入数据...";
            List<Map<String, Object>> insertList = new ArrayList<>();
            for (Map<String, Object> row : validRows) {
                Map<String, Object> item = buildMangaSourceRow(row, type, path);
                insertList.add(item);
            }

            addLog("正在插入 " + insertList.size() + " 条数据到 manga_source...");
            batchInsertMangaSource(insertList);
            processedItems = insertList.size();
            addLog("数据插入完成");

            // 4. 清空 SQLite download 表
            clearSqliteDownload(dbFile);
            addLog("已清空 SQLite download 表");

            // 5. 压缩章节
            currentStatus = "压缩章节...";
            addLog("开始压缩章节...");
            for (Map<String, Object> row : validRows) {
                if (!running) {
                    addLog("任务被停止");
                    currentStatus = "已停止";
                    return;
                }
                String directory = fixDirectory((String) row.get("directory"), path);
                String comicDir = mediaRoot + "/" + typeDir + "/" + path + "/" + directory;
                compressAndCleanComic(row, comicDir);
            }

            currentStatus = "完成";
            addLog("任务完成: 导入 " + processedItems + " 条，压缩 " + zippedChapters + " 个章节，删除 " + deletedFolders + " 个文件夹");

        } catch (Exception e) {
            currentStatus = "异常: " + e.getMessage();
            addLog("任务异常: " + e.getMessage());
            log.error("SQLite 导入任务异常", e);
        } finally {
            running = false;
            // 清理临时文件
            try { dbFile.delete(); } catch (Exception ignored) {}
        }
    }

    // ======================== SQLite 读取 ========================

    private List<Map<String, Object>> readSqliteDownload(File dbFile) throws Exception {
        List<Map<String, Object>> result = new ArrayList<>();
        String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        try (Connection conn = DriverManager.getConnection(url);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM download")) {
            ResultSetMetaData meta = rs.getMetaData();
            int colCount = meta.getColumnCount();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= colCount; i++) {
                    row.put(meta.getColumnLabel(i), rs.getObject(i));
                }
                result.add(row);
            }
        }
        return result;
    }

    private void clearSqliteDownload(File dbFile) throws Exception {
        String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        try (Connection conn = DriverManager.getConnection(url);
             Statement st = conn.createStatement()) {
            st.execute("DELETE FROM download");
        }
    }

    // ======================== 数据转换（基于 DataControl 逻辑） ========================

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildMangaSourceRow(Map<String, Object> row, String type, String path) {
        Map<String, Object> item = new LinkedHashMap<>();
        UUID uuid = UUID.randomUUID();
        item.put("id", uuid.toString());
        item.put("ver", 1);
        item.put("del_flag", 0);
        item.put("create_date", new Date());
        item.put("update_date", new Date());
        item.put("creator_id", "004SVLG0APRAF");
        item.put("creator_name", "czw");
        item.put("updator_id", "004SVLG0APRAF");
        item.put("updator_name", "czw");
        item.put("type", type);
        item.put("picg_id", row.get("id"));
        item.put("title", row.get("title"));
        item.put("subtitle", row.get("subtitle"));
        item.put("download_time", new Date(((Number) row.get("time")).longValue()));
        item.put("directory", row.get("directory"));
        item.put("size", row.get("size"));
        item.put("path", path);

        // 解析 json 字段 — 兼容多种格式
        Object jsonRaw = row.get("json");
        JSONObject jsonRoot = null;
        JSONObject comicItem = null;
        JSONArray chaptersArr = null;

        try {
            if (jsonRaw instanceof JSONObject) {
                jsonRoot = (JSONObject) jsonRaw;
            } else if (jsonRaw instanceof String) {
                jsonRoot = JSONUtil.parseObj((String) jsonRaw);
            }

            if (jsonRoot != null) {
                // 尝试 value 层：{"value": {"comicItem": {...}, "chapters": [...]}}
                JSONObject valueLayer = jsonRoot.getJSONObject("value");
                if (valueLayer != null) {
                    comicItem = valueLayer.getJSONObject("comicItem");
                    chaptersArr = valueLayer.getJSONArray("chapters");
                }
                // 无 value 层：直接 {"comicItem": {...}, "chapters": [...]}
                if (comicItem == null) {
                    comicItem = jsonRoot.getJSONObject("comicItem");
                }
                if (chaptersArr == null) {
                    chaptersArr = jsonRoot.getJSONArray("chapters");
                }
            }
        } catch (Exception e) {
            log.warn("解析 json 字段失败: {}", e.getMessage());
        }

        // 存储 json 原文（jsonb 列，需为 JSON 字符串）
        item.put("json", jsonRaw instanceof String ? (String) jsonRaw : (jsonRoot != null ? jsonRoot.toString() : "{}"));

        // 填充 comicItem 字段
        if (comicItem != null) {
            // creator 是 jsonb，转为 JSON 字符串
            Object creatorObj = comicItem.get("creator");
            item.put("creator", creatorObj != null ? creatorObj.toString() : "null");

            item.put("description", comicItem.get("description"));
            item.put("thumb_url", comicItem.get("thumbUrl"));
            item.put("author", comicItem.get("author"));
            item.put("chinese_team", comicItem.get("chineseTeam"));

            // categories、tags 是 jsonb 数组，转为 JSON 字符串
            Object catsObj = comicItem.get("categories");
            item.put("categories", catsObj != null ? catsObj.toString() : "[]");
            Object tagsObj = comicItem.get("tags");
            item.put("tags", tagsObj != null ? tagsObj.toString() : "[]");

            item.put("likes", comicItem.get("likes"));
            item.put("comments", comicItem.get("comments"));

            Boolean isLiked = (Boolean) comicItem.get("isLiked");
            item.put("is_liked", isLiked != null && isLiked ? 1 : 0);
            Boolean isFavourite = (Boolean) comicItem.get("isFavourite");
            item.put("is_favourite", isFavourite != null && isFavourite ? 1 : 0);

            Object timeObj = comicItem.get("time");
            if (timeObj != null) {
                try {
                    Instant instant = Instant.parse(timeObj.toString());
                    item.put("time", Date.from(instant));
                } catch (Exception e) {
                    item.put("time", new Date());
                }
            } else {
                item.put("time", new Date());
            }
            item.put("pages_count", comicItem.get("pagesCount"));
        } else {
            item.put("time", new Date());
            item.put("categories", "[]");
            item.put("tags", "[]");
        }

        // 章节
        JSONArray chaptersInsert = new JSONArray();
        if (chaptersArr != null) {
            for (int j = 0; j < chaptersArr.size(); j++) {
                JSONObject ch = new JSONObject();
                ch.put("name", chaptersArr.getStr(j));
                ch.put("index", j + 1);
                chaptersInsert.put(ch);
            }
        }
        item.put("chapters", chaptersInsert.toString());

        return item;
    }

    // ======================== 批量插入（手动 SQL，jsonb 列用 ::jsonb 转型） ========================

    private static final String[] INSERT_COLUMNS = {
        "id", "type", "picg_id", "title", "subtitle", "download_time", "directory", "size",
        "json", "path", "creator", "description", "thumb_url", "author", "chinese_team",
        "categories", "tags", "likes", "comments", "is_liked", "is_favourite", "time",
        "pages_count", "chapters", "ver", "del_flag", "create_date", "update_date",
        "creator_id", "creator_name", "updator_id", "updator_name"
    };

    private static final Set<String> JSONB_COLUMNS = Set.of(
        "json", "creator", "categories", "tags", "chapters"
    );

    private static final Set<String> TIMESTAMP_COLUMNS = Set.of(
        "download_time", "time", "create_date", "update_date"
    );

    private static final Set<String> BIGINT_COLUMNS = Set.of("size");

    private static final Set<String> INT_COLUMNS = Set.of(
        "likes", "comments", "pages_count", "ver", "del_flag", "is_liked", "is_favourite"
    );

    private void batchInsertMangaSource(List<Map<String, Object>> rows) throws Exception {
        // 构建 INSERT SQL
        StringBuilder cols = new StringBuilder();
        StringBuilder vals = new StringBuilder();
        for (int i = 0; i < INSERT_COLUMNS.length; i++) {
            if (i > 0) { cols.append(", "); vals.append(", "); }
            cols.append(INSERT_COLUMNS[i]);
            String col = INSERT_COLUMNS[i];
            if (JSONB_COLUMNS.contains(col)) {
                vals.append("{?varchar|").append(col).append("?}::jsonb");
            } else if (TIMESTAMP_COLUMNS.contains(col)) {
                vals.append("{?varchar|").append(col).append("?}::timestamp");
            } else if (BIGINT_COLUMNS.contains(col)) {
                vals.append("{?bigint|").append(col).append("?}");
            } else if (INT_COLUMNS.contains(col)) {
                vals.append("{?integer|").append(col).append("?}");
            } else {
                vals.append("{?varchar|").append(col).append("?}");
            }
        }
        String sql = "INSERT INTO manga_source (" + cols + ") VALUES (" + vals + ")";

        // 逐条执行
        for (Map<String, Object> row : rows) {
            Map<String, Object> params = new LinkedHashMap<>();
            for (String col : INSERT_COLUMNS) {
                Object val = row.get(col);
                params.put(col, val);
            }
            SqlUtil.exec(sql, params);
        }
    }

    // ======================== 章节压缩（复用 MangaZip 逻辑） ========================

    private void compressAndCleanComic(Map<String, Object> row, String comicDir) {
        // 从已构建的 manga_source 行中获取 chapters
        Object chaptersRaw = row.get("json");
        if (chaptersRaw == null) return;

        JSONObject jsonRoot = null;
        JSONObject comicItem = null;
        JSONArray chaptersArr = null;

        try {
            if (chaptersRaw instanceof JSONObject) {
                jsonRoot = (JSONObject) chaptersRaw;
            } else if (chaptersRaw instanceof String) {
                jsonRoot = JSONUtil.parseObj((String) chaptersRaw);
            }
            if (jsonRoot != null) {
                JSONObject valueLayer = jsonRoot.getJSONObject("value");
                if (valueLayer != null) {
                    chaptersArr = valueLayer.getJSONArray("chapters");
                }
                if (chaptersArr == null) {
                    chaptersArr = jsonRoot.getJSONArray("chapters");
                }
            }
        } catch (Exception e) { return; }

        if (chaptersArr == null || chaptersArr.isEmpty()) return;

        String title = row.get("title") != null ? row.get("title").toString() : "未知";

        for (int j = 0; j < chaptersArr.size(); j++) {
            if (!running) break;

            int chapterIndex = j + 1;
            String chapterName = chaptersArr.getStr(j);
            totalChapters++;

            String chapterDir = comicDir + "/" + chapterIndex;
            String zipPath = chapterDir + ".zip";
            File dir = new File(chapterDir);

            // zip 已存在 → 跳过，清理文件夹
            if (new File(zipPath).exists()) {
                if (dir.isDirectory()) {
                    if (deleteDirectory(dir)) {
                        deletedFolders++;
                        addLog("    跳过: " + title + "/" + chapterName + " (zip 已存在，已清理)");
                    }
                }
                continue;
            }

            if (!dir.isDirectory()) continue;

            File[] images = dir.listFiles((d, n) -> n.matches("\\d+\\.(jpg|jpeg|png|webp)"));
            if (images == null || images.length == 0) continue;

            Arrays.sort(images, Comparator.comparingInt(f -> {
                String n = f.getName().replaceAll("[^0-9]", "");
                return n.isEmpty() ? 0 : Integer.parseInt(n);
            }));

            try {
                createZip(images, zipPath);
                zippedChapters++;
                addLog("    压缩: " + title + "/" + chapterName + " (" + images.length + " 张)");

                if (deleteDirectory(dir)) {
                    deletedFolders++;
                }
            } catch (Exception e) {
                addLog("    失败: " + title + "/" + chapterName + " - " + e.getMessage());
            }
        }
    }

    // ======================== 工具方法 ========================

    private String fixDirectory(String directory, String path) {
        if (directory == null) return "";
        if (directory.endsWith(".")) {
            if (path != null && path.compareTo("20251010") >= 0) {
                directory = directory.replaceAll("\\.+$", "");
            } else {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\.+$").matcher(directory);
                if (m.find()) {
                    directory = directory.substring(0, m.start()) + "_".repeat(m.group().length());
                }
            }
        }
        return directory;
    }

    private void createZip(File[] images, String zipPath) throws IOException {
        File zipFile = new File(zipPath);
        File parent = zipFile.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();

        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)))) {
            zos.setLevel(5);
            for (File img : images) {
                ZipEntry entry = new ZipEntry(img.getName());
                entry.setSize(img.length());
                zos.putNextEntry(entry);
                Files.copy(img.toPath(), zos);
                zos.closeEntry();
            }
        }
    }

    private boolean deleteDirectory(File dir) {
        if (dir == null || !dir.exists()) return true;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) deleteDirectory(f);
                else f.delete();
            }
        }
        return dir.delete();
    }

    private void addLog(String msg) {
        String time = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
        logs.add("[" + time + "] " + msg);
        log.info("[SqliteImport] {}", msg);
    }
}
