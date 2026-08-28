package com.example.localPicmaService.tool;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.example.localPicmaService.config.SystemConfig;
import com.example.localPicmaService.tool.SQLTool.SqlUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 漫画章节压缩工具（独立功能）
 * 将每个漫画的每个章节目录打包为 zip 文件
 */
@Lazy
@RestController
@RequestMapping("/tool/manga-zip")
public class MangaZipController {

    private static final Logger log = LoggerFactory.getLogger(MangaZipController.class);

    @Autowired
    private SystemConfig systemConfig;

    private static final Map<String, String> TYPE_DIR = Map.of(
            "漫画", "cartoon",
            "coser", "coser"
    );

    // 任务状态追踪
    private static volatile boolean running = false;
    private static volatile String currentStatus = "空闲";
    private static volatile int totalComics = 0;
    private static volatile int processedComics = 0;
    private static volatile int totalChapters = 0;
    private static volatile int processedChapters = 0;
    private static volatile int skippedChapters = 0;
    private static volatile int errorChapters = 0;
    private static final List<String> logs = Collections.synchronizedList(new ArrayList<>());

    // ======================== 状态查询 ========================

    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("running", running);
        result.put("status", currentStatus);
        result.put("totalComics", totalComics);
        result.put("processedComics", processedComics);
        result.put("totalChapters", totalChapters);
        result.put("processedChapters", processedChapters);
        result.put("skippedChapters", skippedChapters);
        result.put("errorChapters", errorChapters);
        // 返回最近 50 条日志
        synchronized (logs) {
            int from = Math.max(0, logs.size() - 50);
            result.put("logs", new ArrayList<>(logs.subList(from, logs.size())));
        }
        return result;
    }

    // ======================== 启动任务 ========================

    @PostMapping("/start")
    public Map<String, Object> start(@RequestBody(required = false) JSONObject body) {
        if (running) {
            return Map.of("success", false, "error", "任务已在运行中");
        }

        boolean overwrite = body != null && body.getBool("overwrite", false);

        running = true;
        currentStatus = "启动中...";
        logs.clear();
        totalComics = 0; processedComics = 0;
        totalChapters = 0; processedChapters = 0; skippedChapters = 0; errorChapters = 0;

        new Thread(() -> runTask(overwrite), "manga-zip-worker").start();

        return Map.of("success", true, "message", "任务已启动");
    }

    // ======================== 核心逻辑 ========================

    private void runTask(boolean overwrite) {
        try {
            addLog("开始执行漫画章节压缩任务" + (overwrite ? "（强制覆盖模式）" : ""));

            // 1. 查询所有漫画
            List<Map<String, Object>> comics = SqlUtil.query(
                    "SELECT id, type, title, path, directory, chapters FROM manga_source WHERE del_flag = 0 ORDER BY time DESC",
                    Map.of(), 9999);
            if (comics == null || comics.isEmpty()) {
                addLog("没有找到任何漫画数据");
                currentStatus = "完成（无数据）";
                return;
            }

            totalComics = comics.size();
            addLog("共找到 " + totalComics + " 部漫画");

            String mediaRoot = systemConfig.getMediaRootPath();
            if (mediaRoot == null || mediaRoot.isBlank()) {
                addLog("错误：mediaRootPath 未配置");
                currentStatus = "失败（配置缺失）";
                return;
            }

            // 2. 遍历每部漫画
            for (int i = 0; i < comics.size(); i++) {
                if (!running) {
                    addLog("任务被手动停止");
                    currentStatus = "已停止";
                    return;
                }

                Map<String, Object> comic = comics.get(i);
                String comicId = (String) comic.get("id");
                String title = (String) comic.get("title");
                processedComics = i + 1;
                currentStatus = "处理漫画 (" + processedComics + "/" + totalComics + "): " + title;

                // 解析章节列表
                List<Object> chapters = parseJsonArray(comic.get("chapters"));
                if (chapters.isEmpty()) {
                    addLog("  [" + title + "] 无章节数据，跳过");
                    continue;
                }

                String comicDir = resolveFilePath(mediaRoot, comic, "");
                addLog("  [" + title + "] 共 " + chapters.size() + " 个章节，路径: " + comicDir);

                // 3. 遍历每个章节
                for (Object ch : chapters) {
                    if (!running) break;

                    if (!(ch instanceof Map)) continue;
                    Map<?, ?> chMap = (Map<?, ?>) ch;
                    Object idxObj = chMap.get("index");
                    if (idxObj == null) continue;

                    int chapterIndex = Integer.parseInt(idxObj.toString());
                    String chapterName = chMap.get("name") != null ? chMap.get("name").toString() : String.valueOf(chapterIndex);
                    totalChapters++;

                    String chapterDir = resolveFilePath(mediaRoot, comic, String.valueOf(chapterIndex));
                    String zipPath = chapterDir + ".zip";
                    File dir = new File(chapterDir);

                    // 检查是否已存在 zip
                    if (!overwrite && new File(zipPath).exists()) {
                        skippedChapters++;
                        // zip 已存在，清理多余的文件夹
                        if (dir.isDirectory()) {
                            if (deleteDirectory(dir)) {
                                addLog("    跳过: " + chapterName + " (zip 已存在，已清理文件夹)");
                            } else {
                                addLog("    跳过: " + chapterName + " (zip 已存在，文件夹清理失败)");
                            }
                        }
                        continue;
                    }

                    // 扫描图片
                    if (!dir.isDirectory()) {
                        skippedChapters++;
                        continue;
                    }

                    File[] images = dir.listFiles((d, n) -> n.matches("\\d+\\.(jpg|jpeg|png|webp)"));
                    if (images == null || images.length == 0) {
                        skippedChapters++;
                        addLog("    跳过: " + chapterName + " (无图片)");
                        continue;
                    }

                    // 排序
                    Arrays.sort(images, Comparator.comparingInt(f -> {
                        String name = f.getName().replaceAll("[^0-9]", "");
                        return name.isEmpty() ? 0 : Integer.parseInt(name);
                    }));

                    // 打包
                    try {
                        createZip(images, zipPath);
                        processedChapters++;
                        addLog("    完成: " + chapterName + " (" + images.length + " 张) → " + new File(zipPath).getName());

                        // 删除原章节文件夹
                        if (deleteDirectory(dir)) {
                            addLog("    已删除原文件夹: " + dir.getName());
                        } else {
                            addLog("    警告: 原文件夹删除失败: " + dir.getName());
                        }
                    } catch (Exception e) {
                        errorChapters++;
                        addLog("    失败: " + chapterName + " - " + e.getMessage());
                        log.error("压缩失败: {}", zipPath, e);
                    }
                }
            }

            currentStatus = "完成";
            addLog("任务完成：处理 " + processedChapters + " 个章节，跳过 " + skippedChapters + " 个，失败 " + errorChapters + " 个");

        } catch (Exception e) {
            currentStatus = "异常: " + e.getMessage();
            addLog("任务异常: " + e.getMessage());
            log.error("漫画压缩任务异常", e);
        } finally {
            running = false;
        }
    }

    // ======================== 停止任务 ========================

    @PostMapping("/stop")
    public Map<String, Object> stop() {
        if (!running) {
            return Map.of("success", false, "error", "没有正在运行的任务");
        }
        running = false;
        addLog("正在停止任务...");
        return Map.of("success", true);
    }

    // ======================== 工具方法 ========================

    private void createZip(File[] images, String zipPath) throws IOException {
        File zipFile = new File(zipPath);
        // 确保父目录存在
        File parent = zipFile.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();

        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)))) {
            zos.setLevel(5); // 压缩级别
            for (int i = 0; i < images.length; i++) {
                File img = images[i];
                ZipEntry entry = new ZipEntry(img.getName());
                entry.setSize(img.length());
                zos.putNextEntry(entry);
                Files.copy(img.toPath(), zos);
                zos.closeEntry();
            }
        }
    }

    /** 递归删除目录及其所有内容 */
    private boolean deleteDirectory(File dir) {
        if (dir == null || !dir.exists()) return true;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    deleteDirectory(f);
                } else {
                    f.delete();
                }
            }
        }
        return dir.delete();
    }

    private String resolveFilePath(String mediaRoot, Map<String, Object> row, String suffix) {
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

        String base = mediaRoot + "/" + typeDir + "/" + path + "/" + directory;
        if (suffix != null && !suffix.isEmpty()) {
            base += "/" + suffix;
        }
        return base;
    }

    @SuppressWarnings("unchecked")
    private List<Object> parseJsonArray(Object value) {
        if (value == null) return List.of();
        if (value instanceof List) return (List<Object>) value;
        String str = value.toString();
        if (str.isBlank() || "null".equals(str)) return List.of();
        try {
            JSONArray arr = JSONUtil.parseArray(str);
            List<Object> result = new ArrayList<>();
            for (int i = 0; i < arr.size(); i++) result.add(arr.get(i));
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }

    private void addLog(String msg) {
        String time = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
        String line = "[" + time + "] " + msg;
        logs.add(line);
        log.info("[MangaZip] {}", msg);
    }
}
