package com.example.localPicmaService.Class.CommandPackeg;

import com.example.localPicmaService.Class.CommandPackeg.Class.TaskStatus;
import com.example.localPicmaService.Class.CommandPackeg.Service.CommandExecutor;
import com.example.localPicmaService.Class.RollingLogWriter;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * CommandTask 表示「一次命令执行实例」
 * <p>
 * 一个 CommandTask 对应：
 * - 一个 taskId
 * - 一个系统命令
 * - 一个日志文件
 * - 一个运行中的 Process
 * <p>
 * 它的职责是：
 * - 管理命令的生命周期（start / stop）
 * - 维护当前执行状态
 * <p>
 * ⚠️ 不负责：
 * - HTTP / WebSocket
 * - 多任务调度
 */
public class CommandTask {

    private final String taskId;
    private final String command;
    private final Path logFile;
    private String name;
    private final CommandExecutor executor;

    private BufferedWriter inputWriter;


    private Process process;
    private volatile TaskStatus status;

    public CommandTask(
            String taskId,
            String command,
            Path logFile,
            CommandExecutor executor
    ) {
        this.taskId = taskId;
        this.command = command;
        this.logFile = logFile;
        this.executor = executor;
        this.status = TaskStatus.CREATED;
    }

    /**
     * 启动命令执行
     * <p>
     * 调用时机：
     * - CommandManager 创建任务后
     * <p>
     * 执行结果：
     * - 系统进程启动
     * - 日志文件开始被写入
     * - 状态切换为 RUNNING
     */
    public void start() throws Exception {

        RollingLogWriter logWriter = new RollingLogWriter(name, taskId);
        this.process = executor.execute(command, logWriter);

        // 初始化进程输入流（用于后续写命令）
        this.inputWriter = new BufferedWriter(
                new OutputStreamWriter(
                        process.getOutputStream(),
                        StandardCharsets.UTF_8
                )
        );

        this.status = TaskStatus.RUNNING;
    }

    /**
     * 向正在运行的进程发送一条控制台命令
     *
     * @param commandLine 例如：
     *                    - MC: "stop"
     *                    - Zomboid: "save"
     */
    public synchronized void sendCommand(String commandLine) {

        if (status != TaskStatus.RUNNING || inputWriter == null) {
            throw new IllegalStateException("Process is not running");
        }
        try {
            inputWriter.write(commandLine);
            inputWriter.newLine(); // ⬅️ 非常重要，相当于敲 Enter
            inputWriter.flush();
        } catch (IOException e) {
            throw new RuntimeException("Failed to send command", e);
        }
    }

    /**
     * 停止命令执行
     * <p>
     * 调用时机：
     * - 外部 API 请求停止
     * - 系统异常兜底
     */
    public void stop() {
        if (process != null) {
            executor.stop(process);
            status = TaskStatus.STOPPED;
        }
    }

    /**
     * 获取当前任务状态
     * <p>
     * 供外层查询 / UI 展示
     */
    public TaskStatus getStatus() {
        return status;
    }

    /**
     * 获取该任务对应的日志文件路径
     * <p>
     * WebSocket 日志读取时使用
     */
    public Path getLogFile() {
        return logFile;
    }
}

