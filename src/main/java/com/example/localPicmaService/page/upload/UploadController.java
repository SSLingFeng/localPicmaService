package com.example.localPicmaService.page.upload;

import cn.hutool.json.JSONObject;
import com.example.localPicmaService.tool.RustFs.RustFsUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 统一文件上传接口 —— 支持普通上传和分片上传（>50MB 自动分片，每片10MB）
 * 前端调用此接口，后端组装完成后上传到 RustFS
 */
@RestController
@RequestMapping("/api/upload")
public class UploadController {

    private static final long MAX_SINGLE_SIZE = 50L * 1024 * 1024; // 50MB
    private static final String TEMP_DIR = System.getProperty("java.io.tmpdir") + "/upload_chunks";

    @Autowired
    private RustFsUtil rustFsUtil;

    /** 分片上传进度追踪: uploadId -> { totalChunks, receivedChunks, filePaths, targetPath } */
    private static final ConcurrentHashMap<String, ChunkSession> sessions = new ConcurrentHashMap<>();

    // ======================== 普通上传（文件 ≤ 50MB） ========================

    @PostMapping("/single")
    public Map<String, Object> singleUpload(@RequestParam("file") MultipartFile file,
                                            @RequestParam("targetPath") String targetPath) {
        if (!rustFsUtil.isConfigured()) return Map.of("success", false, "error", "RustFS 未配置");
        if (file.isEmpty()) return Map.of("success", false, "error", "文件为空");

        try {
            String dbId = rustFsUtil.upload(file.getInputStream(), targetPath, file.getOriginalFilename(), file.getContentType());
            Map<String, Object> info = rustFsUtil.getFileInfo(dbId);
            String url = info != null && info.get("access_url") != null ? info.get("access_url").toString() : "";
            return Map.of("success", true, "id", dbId, "url", url);
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    // ======================== 分片上传（文件 > 50MB） ========================

    /**
     * 初始化分片上传会话
     */
    @PostMapping("/chunk/init")
    public Map<String, Object> chunkInit(@RequestBody JSONObject body) {
        String uploadId = UUID.randomUUID().toString().replace("-", "");
        int totalChunks = body.getInt("totalChunks", 1);
        String targetPath = body.getStr("targetPath", "");
        String fileName = body.getStr("fileName", "file");

        ChunkSession session = new ChunkSession(totalChunks, targetPath, fileName);
        sessions.put(uploadId, session);

        // 创建临时目录
        new File(TEMP_DIR, uploadId).mkdirs();

        return Map.of("success", true, "uploadId", uploadId);
    }

    /**
     * 上传单个分片
     */
    @PostMapping("/chunk/upload")
    public Map<String, Object> chunkUpload(@RequestParam("file") MultipartFile file,
                                           @RequestParam("uploadId") String uploadId,
                                           @RequestParam("chunkIndex") int chunkIndex) {
        ChunkSession session = sessions.get(uploadId);
        if (session == null) return Map.of("success", false, "error", "会话不存在，请重新初始化");

        try {
            File chunkFile = new File(TEMP_DIR + "/" + uploadId + "/chunk_" + chunkIndex);
            file.transferTo(chunkFile);
            session.receivedChunks.add(chunkIndex);

            return Map.of("success", true, "received", session.receivedChunks.size(), "total", session.totalChunks);
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /**
     * 完成分片上传 —— 合并所有分片并上传到 RustFS
     */
    @PostMapping("/chunk/complete")
    public Map<String, Object> chunkComplete(@RequestBody JSONObject body) {
        String uploadId = body.getStr("uploadId");
        ChunkSession session = sessions.get(uploadId);
        if (session == null) return Map.of("success", false, "error", "会话不存在");

        if (session.receivedChunks.size() != session.totalChunks) {
            return Map.of("success", false, "error",
                    "分片不完整: 已收 " + session.receivedChunks.size() + "/" + session.totalChunks);
        }

        try {
            // 合并分片
            File mergedFile = File.createTempFile("upload_merged_", ".tmp");
            try (OutputStream out = new FileOutputStream(mergedFile)) {
                for (int i = 0; i < session.totalChunks; i++) {
                    File chunk = new File(TEMP_DIR + "/" + uploadId + "/chunk_" + i);
                    try (InputStream in = new FileInputStream(chunk)) {
                        in.transferTo(out);
                    }
                    chunk.delete();
                }
            }

            // 上传到 RustFS
            String dbId;
            try (InputStream in = new FileInputStream(mergedFile)) {
                dbId = rustFsUtil.upload(in, session.targetPath, session.fileName, "application/octet-stream");
            }
            mergedFile.delete();

            // 清理临时目录
            new File(TEMP_DIR, uploadId).delete();
            sessions.remove(uploadId);

            Map<String, Object> info = rustFsUtil.getFileInfo(dbId);
            String url = info != null && info.get("access_url") != null ? info.get("access_url").toString() : "";
            return Map.of("success", true, "id", dbId, "url", url);
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    // ======================== 内部类 ========================

    private static class ChunkSession {
        final int totalChunks;
        final String targetPath;
        final String fileName;
        final Set<Integer> receivedChunks = ConcurrentHashMap.newKeySet();

        ChunkSession(int totalChunks, String targetPath, String fileName) {
            this.totalChunks = totalChunks;
            this.targetPath = targetPath;
            this.fileName = fileName;
        }
    }
}
