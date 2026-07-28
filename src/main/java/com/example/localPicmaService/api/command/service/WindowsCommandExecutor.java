package com.example.localPicmaService.api.command.service;

import com.example.localPicmaService.api.command.log.RollingLogWriter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

public class WindowsCommandExecutor implements CommandExecutor {

    @Override
    public Process execute(String command, RollingLogWriter logWriter) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), Charset.forName("GBK")))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(logWriter.getName() + "  :  " + logWriter.getTaskId() + "  :  " + line);
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
