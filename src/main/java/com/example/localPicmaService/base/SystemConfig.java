package com.example.localPicmaService.base;


import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

/**
 *@Configuration 代表底层配置
 */
@Configuration
@ConfigurationProperties(prefix = "server.base")
public class SystemConfig {


    public String SystemName;


    // 日志文件路径
    public Path LogPath;

    // 单日志文件大小
    public int LogSize;
    // 单日志文件大小的存储单位   KB/MB/GB  只能说这三个类型
    public String LogSizeType;

    //单个实例的日志文件数量
    public int LogFileNum;


    @PostConstruct
    public static void init() {
    }

    /**
     * 判断当前系统是不是windows
     *
     * @return Boolean true 是
     */
    public boolean isWindows() {


        return true;
    }

    /**
     * 获取配置文件内各个配置信息
     */
    public void getConfig() {

    }


    public String getSystemName() {
        return SystemName;
    }

    public void setSystemName(String systemName) {
        SystemName = systemName;
    }

    public Path getLogPath() {
        return LogPath;
    }

    public void setLogPath(Path logPath) {
        LogPath = logPath;
    }

    public int getLogSize() {
        return LogSize;
    }

    public void setLogSize(int logSize) {
        LogSize = logSize;
    }

    public String getLogSizeType() {
        return LogSizeType;
    }

    public void setLogSizeType(String logSizeType) {
        LogSizeType = logSizeType;
    }

    public int getLogFileNum() {
        return LogFileNum;
    }

    public void setLogFileNum(int logFileNum) {
        LogFileNum = logFileNum;
    }

}
