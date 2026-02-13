package com.example.localPicmaService.base;


import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * @Configuration 代表底层配置
 */
@Component
//@ConfigurationProperties(prefix = "server.base")

@Order(Ordered.HIGHEST_PRECEDENCE)
public class SystemConfig {


    public String systemName ="localPicmaService";


    // 日志文件路径
    public Path logPath = Path.of("E:\\log");

    // 单日志文件大小
    public int logSize = 10;
    // 单日志文件大小的存储单位   KB/MB/GB  只能说这三个类型
    public String logSizeType = "MB";

    //单个实例的日志文件数量
    public int logFileNum = 5;


    @PostConstruct
    public void init() {
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

}
