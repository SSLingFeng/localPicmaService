package com.example.localPicmaService.Controller.executeCommand;


import com.example.localPicmaService.Class.CommandPackeg.Class.CommandStartRequest;
import com.example.localPicmaService.Class.CommandPackeg.Class.CommandStartResponse;
import com.example.localPicmaService.Class.CommandPackeg.CommandManager;
import com.example.localPicmaService.base.SystemConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.Map;

@RestController
@RequestMapping("/api/executeCommand/ControllerMinecraft")
public class ControllerMinecraft {

    private CommandManager commandManager;

    public void CommandController(CommandManager commandManager) {
        this.commandManager = commandManager;
    }

    public ControllerMinecraft(CommandManager commandManager) {
        this.commandManager = commandManager;
    }


    /**
     * 启动命令
     * <p>
     * 示例：
     * command = D:\mc\start.bat
     * name    = mc-server
     */
    @PostMapping("/start")
    public CommandStartResponse start(@RequestBody Map<String, Object> data) throws Exception {


        String command = (String) data.get("command");
        String name = (String) data.get("name");

        String taskId = commandManager.execute(command);

        return new CommandStartResponse(
                taskId,
                "",
                "RUNNING"
        );
//        return new CommandStartResponse(
//                taskId,
//                req.getName(),
//                "RUNNING"
//        );
    }
}
