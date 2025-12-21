package com.example.localPicmaService.Class.CommandPackeg.Service;

import com.example.localPicmaService.Class.RollingLogWriter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Linux / Ubuntu 平台命令执行器
 *
 * 使用 /bin/bash -c 执行命令，兼容：
 * - shell 脚本
 * - systemctl
 * - 普通命令
 */
public class LinuxCommandExecutor implements CommandExecutor {

    @Override
    public Process execute(String command, RollingLogWriter logWriter) throws Exception {

        ProcessBuilder pb = new ProcessBuilder(
                "/bin/bash", "-c", command
        );
        pb.redirectErrorStream(true);

        Process process = pb.start();

        new Thread(() -> {
            try (BufferedReader reader =
                         new BufferedReader(
                                 new InputStreamReader(
                                         process.getInputStream(),
                                         StandardCharsets.UTF_8))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    logWriter.writeLine(line);
                }

            } catch (Exception e) {
                try {
                    logWriter.writeLine("[LOG_ERROR] " + e.getMessage());
                } catch (IOException ignored) {}
            }
        }, "cmd-log-writer").start();

        return process;
    }

    @Override
    public void stop(Process process) {
        process.destroy();
    }
}

