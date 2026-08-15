package com.example.localPicmaService.config;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.example.localPicmaService.localPicmaServiceApplication;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.system.ApplicationHome;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SystemConfig {

    private static final String CONFIG_FILE = "config/SystemConfig.json";
    private static String configPath;

    static {
        configPath = resolveAppDir().resolve(CONFIG_FILE).toString();
        System.out.println(">>> SystemConfig 配置路径: " + configPath);
    }

    /**
     * 获取应用根目录（JAR 所在目录或工作目录）
     */
    private static Path resolveAppDir() {
        try {
            var codeSource = SystemConfig.class.getProtectionDomain().getCodeSource();
            if (codeSource != null && codeSource.getLocation() != null) {
                URI uri = codeSource.getLocation().toURI();
                Path loc = Path.of(uri);
                System.out.println(loc.toString());
                // 如果指向的是 JAR 文件，取其父目录；如果是目录（IDE 运行），直接用
                Path appDir = new ApplicationHome(localPicmaServiceApplication.class)
                        .getSource()
                        .toPath()
                        .getParent();
                System.out.println("路径" + appDir.toString());
                return appDir;
            }
        } catch (Exception ignored) {
        }
        // fallback: 当前工作目录
        return Path.of(System.getProperty("user.dir"));
    }

    // ======================== 配置字段 ========================

    private String systemName = "localPicmaService";
    private Path logPath = Path.of("E:\\log");
    private int logSize = 10;
    private String logSizeType = "MB";
    private int logFileNum = 5;
    private String mediaRootPath = "";

    // ======================== RustFS 配置 ========================
    private String rustfsEndpoint = "";
    private String rustfsAccessKey = "";
    private String rustfsSecretKey = "";
    private String rustfsBucket = "";
    private String rustfsPublicUrl = "";
    private boolean rustfsPathStyleAccess = true;

    @PostConstruct
    public void init() {
        loadConfig();
    }

    /**
     * 重新加载配置文件（运行时调用可热更新配置）
     */
    public void reload() {
        System.out.println(">>> SystemConfig 重新加载...");
        loadConfig();
    }

    private void loadConfig() {
        JSONObject config = loadOrCreateConfig();
        applyConfig(config);
        System.out.println(">>> SystemConfig 已加载: " + configPath);
        System.out.println(">>>   systemName    = " + systemName);
        System.out.println(">>>   logPath       = " + logPath);
        System.out.println(">>>   logSize       = " + logSize + logSizeType);
        System.out.println(">>>   logFileNum    = " + logFileNum);
        System.out.println(">>>   mediaRootPath = " + mediaRootPath);
        System.out.println(">>>   rustfsEndpoint = " + rustfsEndpoint);
        System.out.println(">>>   rustfsBucket   = " + rustfsBucket);
    }

    /**
     * 加载配置文件，不存在则创建默认配置
     */
    private JSONObject loadOrCreateConfig() {
        if (FileUtil.exist(configPath)) {
            try {
                String json = FileUtil.readUtf8String(configPath);
                return JSONUtil.parseObj(json);
            } catch (Exception e) {
                System.err.println(">>> SystemConfig 读取失败，使用默认值: " + e.getMessage());
                return createDefaultConfig();
            }
        } else {
            System.out.println(">>> SystemConfig 文件不存在，创建默认配置: " + configPath);
            return createDefaultConfig();
        }
    }

    /**
     * 创建默认配置文件并返回 JSON 对象
     */
    private JSONObject createDefaultConfig() {
        JSONObject config = new JSONObject();

        config.set("sys.systemName", buildEntry(
                "System display name",
                "String",
                "系统显示名称",
                systemName));

        config.set("sys.logPath", buildEntry(
                "Log file storage path (relative or absolute)",
                "Path",
                "日志文件存储路径",
                logPath.toString()));

        config.set("sys.logSize", buildEntry(
                "Single log file max size, used with logSizeType",
                "int",
                "单个日志文件大小，配合 logSizeType 使用",
                logSize));

        config.set("sys.logSizeType", buildEntry(
                "Log size unit: KB / MB / GB",
                "String",
                "日志文件大小单位: KB / MB / GB",
                logSizeType));

        config.set("sys.logFileNum", buildEntry(
                "Max number of log files per task instance",
                "int",
                "单个任务实例的最大日志文件数",
                logFileNum));

        config.set("sys.mediaRootPath", buildEntry(
                "Media root path for comics/coser files",
                "String",
                "媒体资源根目录（漫画/coser 等文件存放路径）",
                mediaRootPath));

        // RustFS 配置
        config.set("sys.rustfsEndpoint", buildEntry(
                "RustFS / S3 endpoint URL, e.g. http://192.168.1.10:9000",
                "String",
                "RustFS 服务地址",
                rustfsEndpoint));

        config.set("sys.rustfsAccessKey", buildEntry(
                "RustFS / S3 access key",
                "String",
                "RustFS 访问密钥 ID",
                rustfsAccessKey));

        config.set("sys.rustfsSecretKey", buildEntry(
                "RustFS / S3 secret key",
                "String",
                "RustFS 访问密钥",
                rustfsSecretKey));

        config.set("sys.rustfsBucket", buildEntry(
                "RustFS / S3 bucket name",
                "String",
                "RustFS 存储桶名称",
                rustfsBucket));

        config.set("sys.rustfsPublicUrl", buildEntry(
                "Public access URL prefix for files, e.g. http://cdn.example.com/bucket",
                "String",
                "文件公开访问 URL 前缀",
                rustfsPublicUrl));

        config.set("sys.rustfsPathStyleAccess", buildEntry(
                "Use path-style access (true) or virtual-hosted-style (false)",
                "boolean",
                "是否使用路径风格访问（兼容 MinIO/RustFS 推荐开启）",
                rustfsPathStyleAccess));

        // 写入文件（自动创建目录）
        FileUtil.mkParentDirs(configPath);
        FileUtil.writeString(config.toStringPretty(), configPath, StandardCharsets.UTF_8);
        System.out.println(">>> 已创建默认配置文件: " + configPath);
        return config;
    }

    /**
     * 从 JSON 配置中读取值并应用到字段
     */
    private void applyConfig(JSONObject config) {
        systemName = getStr(config, "sys.systemName", systemName);
        logPath = Path.of(getStr(config, "sys.logPath", logPath.toString()));
        logSize = getInt(config, "sys.logSize", logSize);
        logSizeType = getStr(config, "sys.logSizeType", logSizeType);
        logFileNum = getInt(config, "sys.logFileNum", logFileNum);
        mediaRootPath = getStr(config, "sys.mediaRootPath", mediaRootPath);
        rustfsEndpoint = getStr(config, "sys.rustfsEndpoint", rustfsEndpoint);
        rustfsAccessKey = getStr(config, "sys.rustfsAccessKey", rustfsAccessKey);
        rustfsSecretKey = getStr(config, "sys.rustfsSecretKey", rustfsSecretKey);
        rustfsBucket = getStr(config, "sys.rustfsBucket", rustfsBucket);
        rustfsPublicUrl = getStr(config, "sys.rustfsPublicUrl", rustfsPublicUrl);
        rustfsPathStyleAccess = getBool(config, "sys.rustfsPathStyleAccess", rustfsPathStyleAccess);
    }

    // ======================== 配置读取工具 ========================

    private String getStr(JSONObject config, String key, String defaultVal) {
        JSONObject entry = config.getJSONObject(key);
        if (entry == null) return defaultVal;
        Object val = entry.get("value");
        return val != null ? val.toString() : defaultVal;
    }

    private int getInt(JSONObject config, String key, int defaultVal) {
        JSONObject entry = config.getJSONObject(key);
        if (entry == null) return defaultVal;
        Object val = entry.get("value");
        if (val instanceof Number) return ((Number) val).intValue();
        try {
            return Integer.parseInt(val.toString());
        } catch (Exception e) {
            return defaultVal;
        }
    }

    private boolean getBool(JSONObject config, String key, boolean defaultVal) {
        JSONObject entry = config.getJSONObject(key);
        if (entry == null) return defaultVal;
        Object val = entry.get("value");
        if (val instanceof Boolean) return (Boolean) val;
        return Boolean.parseBoolean(val.toString());
    }

    /**
     * 构建配置条目
     */
    private JSONObject buildEntry(String text, String type, String zhCn, Object value) {
        JSONObject entry = new JSONObject();
        entry.set("text", text);
        entry.set("type", type);
        entry.set("zh-cn", zhCn);
        entry.set("value", value);
        return entry;
    }

    // ======================== Getter / Setter ========================

    public boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }

    public String getSystemName() {
        return systemName;
    }

    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }

    public Path getLogPath() {
        return logPath;
    }

    public void setLogPath(Path logPath) {
        this.logPath = logPath;
    }

    public int getLogSize() {
        return logSize;
    }

    public void setLogSize(int logSize) {
        this.logSize = logSize;
    }

    public String getLogSizeType() {
        return logSizeType;
    }

    public void setLogSizeType(String logSizeType) {
        this.logSizeType = logSizeType;
    }

    public int getLogFileNum() {
        return logFileNum;
    }

    public void setLogFileNum(int logFileNum) {
        this.logFileNum = logFileNum;
    }

    public String getMediaRootPath() {
        return mediaRootPath;
    }

    public void setMediaRootPath(String mediaRootPath) {
        this.mediaRootPath = mediaRootPath;
    }

    // ======================== RustFS Getter / Setter ========================

    public String getRustfsEndpoint() { return rustfsEndpoint; }
    public void setRustfsEndpoint(String v) { this.rustfsEndpoint = v; }

    public String getRustfsAccessKey() { return rustfsAccessKey; }
    public void setRustfsAccessKey(String v) { this.rustfsAccessKey = v; }

    public String getRustfsSecretKey() { return rustfsSecretKey; }
    public void setRustfsSecretKey(String v) { this.rustfsSecretKey = v; }

    public String getRustfsBucket() { return rustfsBucket; }
    public void setRustfsBucket(String v) { this.rustfsBucket = v; }

    public String getRustfsPublicUrl() { return rustfsPublicUrl; }
    public void setRustfsPublicUrl(String v) { this.rustfsPublicUrl = v; }

    public boolean isRustfsPathStyleAccess() { return rustfsPathStyleAccess; }
    public void setRustfsPathStyleAccess(boolean v) { this.rustfsPathStyleAccess = v; }
}
