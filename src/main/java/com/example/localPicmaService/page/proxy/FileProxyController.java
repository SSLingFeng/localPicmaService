package com.example.localPicmaService.page.proxy;

import com.example.localPicmaService.tool.RustFs.RustFsUtil;
import com.example.localPicmaService.tool.SQLTool.SqlUtil;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.util.Map;

/**
 * RustFS 文件代理接口
 * - /api/public/file  — 无需认证，用于首页图片、封面等公开资源
 * - /api/protected/file — 需要认证，用于受保护内容的下载/预览
 */
@RestController
@RequestMapping("/api")
public class FileProxyController {

    @Autowired
    private RustFsUtil rustFsUtil;

    // ======================== 公开文件（无需认证） ========================

    /**
     * 公开文件代理 — 通过 rustfs_key 或数据库 id 获取文件并返回
     * 参数: key (RustFS key) 或 id (数据库 id)
     */
    @GetMapping("/public/file")
    public void publicFile(@RequestParam(required = false) String key,
                           @RequestParam(required = false) String id,
                           HttpServletResponse response) {
        serveFile(key, id, response);
    }

    // ======================== 受保护文件（需要认证） ========================

    /**
     * 受保护文件代理 — 通过 rustfs_key 或数据库 id 获取文件并返回
     * 参数: key (RustFS key) 或 id (数据库 id)
     */
    @GetMapping("/protected/file")
    public void protectedFile(@RequestParam(required = false) String key,
                              @RequestParam(required = false) String id,
                              HttpServletResponse response) {
        serveFile(key, id, response);
    }

    // ======================== 内部工具 ========================

    private void serveFile(String key, String id, HttpServletResponse response) {
        try {
            String filePath = null;

            if (key != null && !key.isBlank()) {
                // 直接用 RustFS key 查路径
                filePath = rustFsUtil.getFilePathByKey(key);
            } else if (id != null && !id.isBlank()) {
                // 用数据库 id 查路径
                Map<String, Object> info = rustFsUtil.getFileInfo(id);
                if (info != null && info.get("rustfs_key") != null) {
                    filePath = rustFsUtil.getFilePathByKey(info.get("rustfs_key").toString());
                }
            }

            if (filePath == null || filePath.isBlank()) {
                response.setStatus(404);
                return;
            }

            java.io.File file = new java.io.File(filePath);
            if (!file.exists() || !file.isFile()) {
                response.setStatus(404);
                return;
            }

            String contentType = java.nio.file.Files.probeContentType(file.toPath());
            if (contentType == null) contentType = "application/octet-stream";
            response.setContentType(contentType);
            response.setContentLengthLong(file.length());
            response.setHeader("Cache-Control", "public, max-age=3600");

            try (InputStream in = new java.io.FileInputStream(file)) {
                in.transferTo(response.getOutputStream());
            }
        } catch (Exception e) {
            response.setStatus(500);
        }
    }
}
