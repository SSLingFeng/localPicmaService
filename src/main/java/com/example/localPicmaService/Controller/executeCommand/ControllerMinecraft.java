package com.example.localPicmaService.Controller.executeCommand;


import com.example.localPicmaService.Class.CommandPackeg.Class.CommandStartRequest;
import com.example.localPicmaService.Class.CommandPackeg.Class.CommandStartResponse;
import com.example.localPicmaService.Class.CommandPackeg.CommandManager;
import com.example.localPicmaService.Class.ZMessage;
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


//        String command = (String) data.get("command");
        String command = "";
        command = "ping baidu.com";

        String name = (String) data.get("name");

        String taskId = commandManager.execute(name, command);

        return new CommandStartResponse(
                taskId,
                name,
                "RUNNING"
        );
    }

    @PostMapping("/stop")
    public ZMessage stop(@RequestBody Map<String, Object> data) throws Exception {
        ZMessage zmessage = new ZMessage();


        //        String command = (String) data.get("command");
        String command = "";
        command = "ping baidu.com";

        String name = (String) data.get("name");
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
