package com.example.localPicmaService.api.command.service;

import com.example.localPicmaService.api.command.model.TaskStatus;
import com.example.localPicmaService.config.SystemConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CommandManager {

    private final Map<String, CommandTask> tasks = new ConcurrentHashMap<>();

    @Autowired
    private SystemConfig systemConfig;

    private final CommandExecutor executor;

    public CommandManager(SystemConfig config) {
        this.executor = config.isWindows()
                ? new WindowsCommandExecutor()
                : new LinuxCommandExecutor();
    }

    public String execute(String name, String command) throws Exception {
        String taskId = UUID.randomUUID().toString();
        Path logFile = systemConfig.getLogPath();
        CommandTask task = new CommandTask(name, taskId, command, logFile, executor);
        task.start();
        tasks.put(taskId, task);
        return taskId;
    }

    public void stop(String taskId) {
        CommandTask task = tasks.get(taskId);
        if (task != null) {
            task.stop();
        }
    }

    public Path getLogFile(String taskId) {
        return tasks.get(taskId).getLogFile();
    }

    public TaskStatus getStatus(String taskId) {
        return tasks.get(taskId).getStatus();
    }

    public void sendCommand(String taskId, String commandLine) {
        CommandTask task = tasks.get(taskId);
        if (task == null) {
            throw new IllegalArgumentException("Task not found");
        }
        task.sendCommand(commandLine);
    }
}
