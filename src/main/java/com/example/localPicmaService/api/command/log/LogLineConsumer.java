package com.example.localPicmaService.api.command.log;

@FunctionalInterface
public interface LogLineConsumer {
    void onLine(String line) throws Exception;
}
