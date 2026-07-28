package com.example.localPicmaService.api.command.service;

import com.example.localPicmaService.api.command.model.TaskStatus;
import com.example.localPicmaService.api.command.log.RollingLogWriter;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public class CommandTask {
    private final String taskId;
    private final String command;
    private final Path logFile;
    private String name;
    private final CommandExecutor executor;
    private BufferedWriter inputWriter;
    private Process process;
    private volatile TaskStatus status;

    public CommandTask(String name, String taskId, String command, Path logFile, CommandExecutor executor) {
        this.taskId = taskId;
        this.command = command;
        this.name = name;
        this.logFile = logFile;
        this.executor = executor;
        this.status = TaskStatus.CREATED;
    }

    public void start() throws Exception {
        RollingLogWriter logWriter = new RollingLogWriter(name, taskId);
        this.process = executor.execute(command, logWriter);
        this.inputWriter = new BufferedWriter(
                new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        if (logWriter != null) {
            logWriter.writeLine("[INPUT] : " + command);
        }
        this.status = TaskStatus.RUNNING;
    }

    public synchronized void sendCommand(String commandLine) {
        if (status != TaskStatus.RUNNING || inputWriter == null) {
            throw new IllegalStateException("Process is not running");
        }
        try {
            inputWriter.write(commandLine);
            inputWriter.newLine();
            inputWriter.flush();
        } catch (IOException e) {
            throw new RuntimeException("Failed to send command", e);
        }
    }

    public void stop() {
        if (process != null) {
            executor.stop(process);
            status = TaskStatus.STOPPED;
        }
    }

    public TaskStatus getStatus() { return status; }
    public Path getLogFile() { return logFile; }
}
