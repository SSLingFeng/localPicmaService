package com.example.localPicmaService.page.rustfs.controller;

import cn.hutool.json.JSONObject;
import com.example.localPicmaService.tool.RustFs.RustFsUtil;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.example.localPicmaService.tool.SQLTool.SqlUtil;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * RustFS 文件管理接口
 */
@RestController
@RequestMapping("/page/rustfs/api")
public class RustFsController {

    @Autowired
    private RustFsUtil rustFsUtil;

    /**
     * 上传文件
     *
     * @param file       文件
     * @param objectPath RustFS 存储路径（含文件名），如 "images/2025/pic.jpg"
     * @return 数据库记录 id 和访问 URL
     */
    @PostMapping("/upload")
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file,
                                      @RequestParam("objectPath") String objectPath) {
        try {
            String contentType = file.getContentType();
            String dbId = rustFsUtil.upload(file.getInputStream(), objectPath, file.getOriginalFilename(), contentType);
            Map<String, Object> info = rustFsUtil.getFileInfo(dbId);
            return Map.of("success", true, "id", dbId, "data", info != null ? info : Map.of());
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /**
     * 下载文件（通过数据库 ID 查询后从 RustFS 获取）
     */
    @GetMapping("/download")
    public void download(@RequestParam String id, HttpServletResponse response) {
        try {
            Map<String, Object> info = rustFsUtil.getFileInfo(id);
            if (info == null) { response.setStatus(404); return; }

            String fileName = info.get("file_name") != null ? info.get("file_name").toString() : "file";
            String format = info.get("file_format") != null ? info.get("file_format").toString() : "";

            response.setContentType(getMimeType(format));
            response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");

            try (InputStream in = rustFsUtil.download(id)) {
                in.transferTo(response.getOutputStream());
            }
        } catch (Exception e) {
            response.setStatus(500);
        }
    }

    /**
     * 删除文件（通过数据库 ID，同时删除 RustFS 对象和数据库记录）
     */
    @PostMapping("/delete")
    public Map<String, Object> delete(@RequestBody JSONObject body) {
        String id = body.getStr("id");
        if (id == null || id.isBlank()) return Map.of("success", false, "error", "缺少 id");

        try {
            boolean deleted = rustFsUtil.delete(id);
            if (deleted) return Map.of("success", true);
            else return Map.of("success", false, "error", "文件记录不存在");
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /**
     * 文件列表
     */
    @GetMapping("/list")
    public Map<String, Object> list() throws Exception {
        List<Map<String, Object>> rows = SqlUtil.query(
                "SELECT id, file_name, file_format, file_size, access_url, rustfs_key, create_date "
                        + "FROM rustfs_file ORDER BY create_date DESC", Map.of(), 200);
        return Map.of("success", true, "items", rows != null ? rows : List.of());
    }

    /**
     * 查询文件信息
     */
    @GetMapping("/info")
    public Map<String, Object> info(@RequestParam String id) throws Exception {
        Map<String, Object> info = rustFsUtil.getFileInfo(id);
        if (info == null) return Map.of("success", false, "error", "记录不存在");
        return Map.of("success", true, "data", info);
    }

    private String getMimeType(String format) {
        if (format == null) return "application/octet-stream";
        return switch (format.toLowerCase()) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "pdf" -> "application/pdf";
            case "mp4" -> "video/mp4";
            case "zip" -> "application/zip";
            default -> "application/octet-stream";
        };
    }
}
