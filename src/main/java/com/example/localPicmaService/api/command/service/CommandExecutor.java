package com.example.localPicmaService.api.command.service;

import com.example.localPicmaService.api.command.log.RollingLogWriter;

public interface CommandExecutor {
    Process execute(String command, RollingLogWriter logWriter) throws Exception;
    void stop(Process process);
}
