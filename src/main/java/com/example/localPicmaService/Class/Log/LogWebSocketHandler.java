package com.example.localPicmaService.Class.Log;

import com.example.localPicmaService.Class.CommandPackeg.CommandManager;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.File;
import java.nio.file.Path;

@Component
public class LogWebSocketHandler extends TextWebSocketHandler {

    private final CommandManager commandManager;

    public LogWebSocketHandler(CommandManager commandManager) {
        this.commandManager = commandManager;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session)
            throws Exception {

        String taskId = extractTaskId(session);

        Path logDir = commandManager.getLogFile(taskId);

        // 启动日志 tail 线程
        new Thread(() -> tailLogs(session, logDir)).start();
    }

    private void tailLogs(WebSocketSession session, Path logDir) {
        try {
            LogTailer.tail(logDir, line -> {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(line));
                }
            });
        } catch (Exception e) {
            // ignore
        }
    }

    private String extractTaskId(WebSocketSession session) {
        return session.getUri().getPath()
                .substring(session.getUri().getPath().lastIndexOf("/") + 1);
    }
}
