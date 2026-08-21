package com.example.localPicmaService.tool.RustFs;

import com.example.localPicmaService.tool.SQLTool.SqlUtil;
import com.example.localPicmaService.tool.Valkey.ValkeyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.InputStream;
import java.util.*;

/**
 * RustFS 文件管理工具 —— 提供文件的上传、下载、删除能力，并与数据库表 rustfs_file 联动。
 *
 * <h3>功能</h3>
 * <ul>
 *   <li>上传文件到 RustFS 指定路径，同步在数据库中记录文件元信息</li>
 *   <li>根据数据库 ID 删除文件（同时删除 RustFS 对象和数据库记录）</li>
 *   <li>根据数据库 ID 下载文件（查询数据库获取 RustFS key，再从 RustFS 获取流）</li>
 * </ul>
 *
 * <h3>数据库表</h3>
 * <p>rustfs_file，字段: id, file_name, file_format, file_size, access_url, rustfs_key, create_date</p>
 */
@Component
public class RustFsUtil {

    private static final Logger log = LoggerFactory.getLogger(RustFsUtil.class);

    @Autowired
    private RustFsConfig rustFsConfig;

    @Autowired
    private ValkeyUtil valkeyUtil;

    public boolean isConfigured() {
        return rustFsConfig.isConfigured();
    }

    // ======================== 上传 ========================

    /**
     * 上传文件到 RustFS 并同步写入数据库记录
     *
     * @param inputStream 文件输入流
     * @param objectPath  RustFS 中的存储路径（含文件名），如 "images/2025/pic.jpg"
     * @param fileName    原始文件名（用于数据库记录）
     * @param contentType MIME 类型，可为 null（自动检测或使用默认值）
     * @return 数据库记录 id（UUID）
     */
    public String upload(InputStream inputStream, String objectPath, String fileName, String contentType) throws Exception {
        if (!rustFsConfig.isConfigured()) throw new IllegalStateException("RustFS 未配置");
        S3Client client = rustFsConfig.getS3Client();
        String bucket = rustFsConfig.getBucket();

        // 计算文件大小：先读到临时 byte 数组
        byte[] data;
        try {
            data = inputStream.readAllBytes();
        } catch (Exception e) {
            throw new RuntimeException("读取文件流失败", e);
        }

        // 构建 PutObject 请求
        PutObjectRequest.Builder reqBuilder = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectPath);
        if (contentType != null && !contentType.isBlank()) {
            reqBuilder.contentType(contentType);
        }

        client.putObject(reqBuilder.build(), RequestBody.fromBytes(data));

        // 拼接公开访问 URL
        String publicUrl = buildAccessUrl(objectPath);

        // 写入数据库
        String id = UUID.randomUUID().toString().replace("-", "");
        String fileFormat = extractFormat(fileName);
        SqlUtil.exec(
                "INSERT INTO rustfs_file (id, file_name, file_format, file_size, access_url, rustfs_key, create_date) "
                        + "VALUES ({?varchar|id?}, {?varchar|fileName?}, {?varchar|format?}, {?bigint|size?}, "
                        + "{?varchar|url?}, {?varchar|key?}, NOW())",
                Map.of("id", id,
                        "fileName", fileName,
                        "format", fileFormat,
                        "size", (long) data.length,
                        "url", publicUrl,
                        "key", objectPath));

        log.info("RustFS 上传成功: key={}, size={}, dbId={}", objectPath, data.length, id);
        return id;
    }

    /**
     * 上传文件到 RustFS 指定路径（MIME 类型自动设为 application/octet-stream）
     */
    public String upload(InputStream inputStream, String objectPath, String fileName) throws Exception {
        return upload(inputStream, objectPath, fileName, "application/octet-stream");
    }

    // ======================== 下载 ========================

    /**
     * 根据数据库 ID 获取文件流
     *
     * @param dbId 数据库记录 ID
     * @return S3Object 的 InputStream，调用方需自行关闭
     */
    public InputStream download(String dbId) throws Exception {
        if (!rustFsConfig.isConfigured()) throw new IllegalStateException("RustFS 未配置");
        String rustfsKey = getRustfsKeyById(dbId);
        if (rustfsKey == null) throw new RuntimeException("文件记录不存在: " + dbId);

        S3Client client = rustFsConfig.getS3Client();
        GetObjectRequest req = GetObjectRequest.builder()
                .bucket(rustFsConfig.getBucket())
                .key(rustfsKey)
                .build();
        return client.getObject(req);
    }

    /**
     * 根据数据库 ID 获取文件元信息
     *
     * @param dbId 数据库记录 ID
     * @return 包含 file_name, file_format, file_size, access_url, rustfs_key 的 Map，不存在返回 null
     */
    public Map<String, Object> getFileInfo(String dbId) throws Exception {
        return SqlUtil.row(
                "SELECT id, file_name, file_format, file_size, access_url, rustfs_key, create_date "
                        + "FROM rustfs_file WHERE id = {?varchar|id?}",
                Map.of("id", dbId));
    }

    /**
     * 根据 Valkey key 获取本地文件路径
     * 用于漫画封面、章节图片等通过 Valkey 缓存的文件
     *
     * @param valkeyKey Valkey 键
     * @return 本地文件路径，不存在返回 null
     */
    public String getFilePathByKey(String valkeyKey) throws Exception {
        // 从 Valkey 读取路径
        String path = valkeyUtil.get(valkeyKey);
        if (path != null && !path.isBlank()) {
            return path;
        }
        // fallback: 从 rustfs_file 表查
        Map<String, Object> row = SqlUtil.row(
                "SELECT access_url FROM rustfs_file WHERE rustfs_key = {?varchar|k?}",
                Map.of("k", valkeyKey));
        if (row != null && row.get("access_url") != null) {
            return row.get("access_url").toString();
        }
        return null;
    }

    // ======================== 删除 ========================

    /**
     * 根据数据库 ID 删除文件（同时删除 RustFS 对象和数据库记录）
     *
     * @param dbId 数据库记录 ID
     * @return true=删除成功，false=记录不存在
     */
    public boolean delete(String dbId) throws Exception {
        if (!rustFsConfig.isConfigured()) throw new IllegalStateException("RustFS 未配置");
        String rustfsKey = getRustfsKeyById(dbId);
        if (rustfsKey == null) return false;

        // 删除 RustFS 对象
        S3Client client = rustFsConfig.getS3Client();
        DeleteObjectRequest req = DeleteObjectRequest.builder()
                .bucket(rustFsConfig.getBucket())
                .key(rustfsKey)
                .build();
        client.deleteObject(req);

        // 删除数据库记录
        SqlUtil.exec("DELETE FROM rustfs_file WHERE id = {?varchar|id?}", Map.of("id", dbId));

        log.info("RustFS 删除成功: key={}, dbId={}", rustfsKey, dbId);
        return true;
    }

    // ======================== 内部工具 ========================

    /** 根据数据库 ID 查询 rustfs_key */
    private String getRustfsKeyById(String dbId) throws Exception {
        Map<String, Object> row = SqlUtil.row(
                "SELECT rustfs_key FROM rustfs_file WHERE id = {?varchar|id?}",
                Map.of("id", dbId));
        if (row == null) return null;
        Object key = row.get("rustfs_key");
        return key != null ? key.toString() : null;
    }

    /** 拼接公开访问 URL */
    private String buildAccessUrl(String objectPath) {
        String base = rustFsConfig.getPublicUrl();
        if (base == null || base.isBlank()) {
            // fallback: 用 endpoint + bucket
            base = rustFsConfig.getRustfsEndpoint().replaceAll("/+$", "")
                    + "/" + rustFsConfig.getBucket();
        }
        return base.replaceAll("/+$", "") + "/" + objectPath;
    }

    /** 从文件名提取格式后缀 */
    private String extractFormat(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot + 1).toLowerCase() : "";
    }
}
