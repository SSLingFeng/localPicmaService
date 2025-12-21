package com.example.localPicmaService.Class.CommandPackeg.Service;

import com.example.localPicmaService.Class.RollingLogWriter;


/**
 * 命令执行器抽象接口
 *
 * 这一层的职责：
 * 1. 屏蔽操作系统差异（Windows / Linux）
 * 2. 提供最基础的「执行」和「停止」能力
 *
 * ⚠️ 重要原则：
 * - 不关心日志内容
 * - 不关心 WebSocket / HTTP
 * - 只关心「如何在当前系统上启动 / 停止进程」
 */
public interface CommandExecutor {


    /**
     * 执行命令，并将标准输出/错误输出重定向到日志文件
     *
     * @param command  要执行的命令字符串
     *                 - Windows: bat / exe / cmd
     *                 - Linux: shell / systemctl / bash
     *
     * @param logFile  日志文件路径（由上层统一规划）
     *
     * @return Process
     *         返回 Java Process 对象，作为后续「结束命令」的控制句柄
     *
     * @throws Exception
     *         启动进程失败（路径错误、权限不足等）
     */
    Process execute(
            String command,
            RollingLogWriter logWriter
    ) throws Exception;

    /**
     * 结束正在运行的命令
     *
     * @param process execute() 返回的 Process 实例
     *
     * 说明：
     * - Windows / Linux 都支持 destroy()
     * - 是否强制杀死，可在此层扩展 destroyForcibly()
     */
    void stop(Process process);

}
