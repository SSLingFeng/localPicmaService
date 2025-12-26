package com.example.localPicmaService.Class.CommandPackeg;


import com.example.localPicmaService.Class.CommandPackeg.Class.TaskStatus;
import com.example.localPicmaService.Class.CommandPackeg.Service.CommandExecutor;
import com.example.localPicmaService.Class.CommandPackeg.Service.LinuxCommandExecutor;
import com.example.localPicmaService.Class.CommandPackeg.Service.WindowsCommandExecutor;
import com.example.localPicmaService.base.SystemConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CommandManager
 * <p>
 * 全局命令任务管理器
 * <p>
 * 职责：
 * - 创建命令任务
 * - 根据 taskId 查找 / 停止任务
 * - 屏蔽操作系统差异
 * <p>
 * ⚠️ 这是业务层和底层执行层之间的“中间层”
 */
@Service
public class CommandManager {

    /**
     * 保存所有正在运行或历史的命令任务
     * <p>
     * key   : taskId
     * value : CommandTask
     */
    private final Map<String, CommandTask> tasks = new ConcurrentHashMap<>();

    @Autowired
    private SystemConfig systemConfig;

    private final CommandExecutor executor;

    /**
     * 根据配置决定使用 Windows / Linux 执行器
     */
    public CommandManager(SystemConfig config) {
        this.executor = config.isWindows()
                ? new WindowsCommandExecutor()
                : new LinuxCommandExecutor();
    }

    /**
     * 创建并执行命令
     * <p>
     * 调用时机：
     * - REST API: 执行命令
     *
     * @return taskId
     * 业务系统用于后续查询 / 停止 / 读取日志
     */
    public String execute(String name, String command) throws Exception {
        String taskId = UUID.randomUUID().toString();


        Path logFile = systemConfig.getLogPath();

//        logFile


        CommandTask task = new CommandTask(
                name, taskId, command, logFile, executor
        );

        task.start();
        tasks.put(taskId, task);

        return taskId;
    }

    /**
     * 停止指定 taskId 的命令
     */
    public void stop(String taskId) {
        CommandTask task = tasks.get(taskId);
        if (task != null) {
            task.stop();
        }
    }

    /**
     * 获取任务对应的日志文件路径
     * <p>
     * WebSocket 日志读取专用
     */
    public Path getLogFile(String taskId) {
        return tasks.get(taskId).getLogFile();
    }

    /**
     * 获取任务当前状态
     */
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
