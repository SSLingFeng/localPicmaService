package com.example.localPicmaService.Class.Log;

@FunctionalInterface
public interface LogLineConsumer {
    void onLine(String line) throws Exception;
}
