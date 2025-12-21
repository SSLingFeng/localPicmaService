package com.example.localPicmaService.Class.CommandPackeg.Service;

import com.example.localPicmaService.Class.RollingLogWriter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

/**
 * Windows 平台命令执行器
 * <p>
 * 仅负责：
 * - 使用 cmd.exe 启动命令
 * - 重定向输出到日志文件
 * <p>
 * ⚠️ 不处理：
 * - 任务状态
 * - 日志读取
 * - 多实例管理
 */
public class WindowsCommandExecutor implements CommandExecutor {

    @Override
    public Process execute(String command, RollingLogWriter logWriter) throws Exception {

        ProcessBuilder pb = new ProcessBuilder(
                "cmd.exe", "/c", command
        );
        pb.redirectErrorStream(true);

        Process process = pb.start();

        // 异步读取输出并写入日志
        new Thread(() -> {
            try (BufferedReader reader =
                         new BufferedReader(
                                 new InputStreamReader(
                                         process.getInputStream(),
                                         Charset.forName("GBK")))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(logWriter.getName() + "  :  " + logWriter.getTaskId() + "  :  " + line);
                    logWriter.writeLine(line);
                }

            } catch (Exception e) {
                try {
                    logWriter.writeLine("[LOG_ERROR] " + e.getMessage());
                } catch (IOException ignored) {
                }
            }
        }, "cmd-log-writer").start();

        return process;
    }

    @Override
    public void stop(Process process) {
        process.destroy();
    }
}
