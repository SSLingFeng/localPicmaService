package com.example.localPicmaService.api.command.log;

import cn.hutool.core.date.DateUtil;
import com.example.localPicmaService.config.SystemConfig;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class RollingLogWriter {
    private Path baseDir;
    private String name;
    private String taskId;
    private long maxFileSizeBytes;
    private int maxFileNum;
    private BufferedWriter writer;
    private Path currentLogFile;
    private SystemConfig config = new SystemConfig();

    public RollingLogWriter(String name, String taskId) throws IOException {
        this.name = name;
        this.taskId = taskId;
        this.maxFileNum = config.getLogFileNum();
        this.maxFileSizeBytes = parseSize(config.getLogSize(), config.getLogSizeType());
        this.baseDir = config.getLogPath()
                .resolve("Command")
                .resolve(name + "-" + DateUtil.format(new Date(), "yyyy_MM_dd_HH-mm-ss") + "-" + taskId);
        Files.createDirectories(baseDir);
        rotateFileIfNeeded();
    }

    public synchronized void writeLine(String line) throws IOException {
        rotateFileIfNeeded();
        writer.write(line);
        writer.newLine();
        writer.flush();
    }

    private void rotateFileIfNeeded() throws IOException {
        if (currentLogFile == null || Files.size(currentLogFile) >= maxFileSizeBytes) {
            closeWriter();
            cleanupOldFiles();
            String fileName = String.format("%s-%s-%s.log", name, taskId,
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
            currentLogFile = baseDir.resolve(fileName);
            writer = Files.newBufferedWriter(currentLogFile, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
    }

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
        if (writer != null) writer.close();
    }

    private long parseSize(int size, String type) {
        return switch (type.toUpperCase()) {
            case "KB" -> size * 1024L;
            case "MB" -> size * 1024L * 1024L;
            case "GB" -> size * 1024L * 1024L * 1024L;
            default -> throw new IllegalArgumentException("Invalid logSizeType");
        };
    }

    public Path getBaseDir() { return baseDir; }
    public String getName() { return name; }
    public String getTaskId() { return taskId; }
    public long getMaxFileSizeBytes() { return maxFileSizeBytes; }
    public int getMaxFileNum() { return maxFileNum; }
    public BufferedWriter getWriter() { return writer; }
    public void setWriter(BufferedWriter writer) { this.writer = writer; }
    public Path getCurrentLogFile() { return currentLogFile; }
    public void setCurrentLogFile(Path currentLogFile) { this.currentLogFile = currentLogFile; }
    public SystemConfig getConfig() { return config; }
    public void setConfig(SystemConfig config) { this.config = config; }
    public void setBaseDir(Path baseDir) { this.baseDir = baseDir; }
    public void setName(String name) { this.name = name; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public void setMaxFileSizeBytes(long maxFileSizeBytes) { this.maxFileSizeBytes = maxFileSizeBytes; }
    public void setMaxFileNum(int maxFileNum) { this.maxFileNum = maxFileNum; }
}
