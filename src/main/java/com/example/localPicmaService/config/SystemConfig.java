package com.example.localPicmaService.config;

import jakarta.annotation.PostConstruct;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SystemConfig {

    public String systemName = "localPicmaService";
    public Path logPath = Path.of("E:\\log");
    public int logSize = 10;
    public String logSizeType = "MB";
    public int logFileNum = 5;

    @PostConstruct
    public void init() {
    }

    public boolean isWindows() {
        return true;
    }

    public void getConfig() {
    }

    public String getSystemName() { return systemName; }
    public void setSystemName(String systemName) { this.systemName = systemName; }
    public Path getLogPath() { return logPath; }
    public void setLogPath(Path logPath) { this.logPath = logPath; }
    public int getLogSize() { return logSize; }
    public void setLogSize(int logSize) { this.logSize = logSize; }
    public String getLogSizeType() { return logSizeType; }
    public void setLogSizeType(String logSizeType) { this.logSizeType = logSizeType; }
    public int getLogFileNum() { return logFileNum; }
    public void setLogFileNum(int logFileNum) { this.logFileNum = logFileNum; }
}
