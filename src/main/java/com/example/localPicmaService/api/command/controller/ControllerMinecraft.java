package com.example.localPicmaService.api.command.controller;

import com.example.localPicmaService.api.command.model.CommandStartRequest;
import com.example.localPicmaService.api.command.model.CommandStartResponse;
import com.example.localPicmaService.api.command.service.CommandManager;
import com.example.localPicmaService.common.ZMessage;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/executeCommand/ControllerMinecraft")
public class ControllerMinecraft {

    private CommandManager commandManager;

    public ControllerMinecraft(CommandManager commandManager) {
        this.commandManager = commandManager;
    }

    @PostMapping("/start")
    public CommandStartResponse start(@RequestBody Map<String, Object> data) throws Exception {
        String command = "ping baidu.com";
        String name = (String) data.get("name");
        String taskId = commandManager.execute(name, command);
        return new CommandStartResponse(taskId, name, "RUNNING");
    }

    @PostMapping("/stop")
    public ZMessage stop(@RequestBody Map<String, Object> data) throws Exception {
        ZMessage zmessage = new ZMessage();
        String taskId = (String) data.get("taskId");
        if (taskId.isEmpty()) {
            zmessage.setfail("任务ID不能为空");
            return zmessage;
        }
        try {
            commandManager.stop(taskId);
            zmessage.setSucc("成功");
        } catch (Exception e) {
            zmessage.setfail("失败" + e.getMessage());
        }
        return zmessage;
    }
}
