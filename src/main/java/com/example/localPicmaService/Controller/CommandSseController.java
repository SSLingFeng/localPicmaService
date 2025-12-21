package com.example.localPicmaService.Controller;

import com.example.localPicmaService.Class.CommandService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@RestController
public class CommandSseController {

    private final CommandService commandService;

    public CommandSseController(CommandService commandService) {
        this.commandService = commandService;
    }

    @GetMapping(value = "/api/EXcuteCommand", produces = "text/event-stream")
    public SseEmitter pingBySse() {

        SseEmitter emitter = new SseEmitter(0L);
        String cmd = "E:\\SteamLibrary\\steamapps\\common\\ProjectZomboid\\ProjectZomboid64.bat";
//        cmd = "ping baidu.com";
        new Thread(() -> {
            try {
                commandService.execute(cmd, line -> {
                            try {
                                System.out.println(line);
                                emitter.send(line);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                );
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        }).start();

        return emitter;
    }
}
