package com.example.localPicmaService.Class;

import com.example.localPicmaService.base.SystemConfig;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

/**
 * RollingLogWriter
 * <p>
 * 职责：
 * - 管理单个命令实例的日志文件
 * - 控制单文件大小
 * - 控制文件数量
 * <p>
 * ⚠️ 一个 CommandTask 只会持有一个 RollingLogWriter
 */
public class RollingLogWriter {

    private  Path baseDir;
    private  String name;
    private  String taskId;
    private  long maxFileSizeBytes;
    private  int maxFileNum;

    private BufferedWriter writer;
    private Path currentLogFile;
    private SystemConfig config;

    public RollingLogWriter(String name, String taskId) throws IOException {
        this.name = name;
        this.taskId = taskId;
        this.maxFileNum = config.getLogFileNum();
        this.maxFileSizeBytes = parseSize(
                config.getLogSize(),
                config.getLogSizeType()
        );

        // System.logPath/Command/{name-taskId}
        this.baseDir = config.getLogPath()
                .resolve("Command")
                .resolve(name + "-" + taskId);

        Files.createDirectories(baseDir);

        rotateFileIfNeeded();
    }

    /**
     * 写入一行日志
     * 每次写入前都会检查文件大小
     */
    public synchronized void writeLine(String line) throws IOException {
        rotateFileIfNeeded();
        writer.write(line);
        writer.newLine();
        writer.flush();
    }

    /**
     * 判断是否需要切换日志文件
     */
    private void rotateFileIfNeeded() throws IOException {
        if (currentLogFile == null ||
                Files.size(currentLogFile) >= maxFileSizeBytes) {

            closeWriter();
            cleanupOldFiles();

            String fileName = String.format(
                    "%s-%s-%s.log",
                    name,
                    taskId,
                    LocalDateTime.now()
                            .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
            );

            currentLogFile = baseDir.resolve(fileName);
            writer = Files.newBufferedWriter(
                    currentLogFile,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        }
    }

    /**
     * 超过最大数量时，删除最老的日志文件
     */
    private void cleanupOldFiles() throws IOException {
        List<Path> logs = Files.list(baseDir)
                .filter(p -> p.toString().endsWith(".log"))
                .sorted(Comparator.comparingLong(p -> p.toFile().lastModified()))
                .toList();

        while (logs.size() >= maxFileNum) {
            Files.deleteIfExists(logs.get(0));
            logs = logs.subList(1, logs.size());
        }
    }

    private void closeWriter() throws IOException {
        if (writer != null) {
            writer.close();
        }
    }

    private long parseSize(int size, String type) {
        return switch (type.toUpperCase()) {
            case "KB" -> size * 1024L;
            case "MB" -> size * 1024L * 1024L;
            case "GB" -> size * 1024L * 1024L * 1024L;
            default -> throw new IllegalArgumentException("Invalid logSizeType");
        };
    }


    public Path getBaseDir() {
        return baseDir;
    }

    public String getName() {
        return name;
    }

    public String getTaskId() {
        return taskId;
    }

    public long getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    public int getMaxFileNum() {
        return maxFileNum;
    }

    public BufferedWriter getWriter() {
        return writer;
    }

    public void setWriter(BufferedWriter writer) {
        this.writer = writer;
    }

    public Path getCurrentLogFile() {
        return currentLogFile;
    }

    public void setCurrentLogFile(Path currentLogFile) {
        this.currentLogFile = currentLogFile;
    }

    public SystemConfig getConfig() {
        return config;
    }

    public void setConfig(SystemConfig config) {
        this.config = config;
    }

    public void setBaseDir(Path baseDir) {
        this.baseDir = baseDir;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public void setMaxFileSizeBytes(long maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    public void setMaxFileNum(int maxFileNum) {
        this.maxFileNum = maxFileNum;
    }
}
