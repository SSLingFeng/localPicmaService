package com.example.localPicmaService.Class.CommandPackeg.Class;

import lombok.Data;

@Data
public class CommandStartRequest {
    private String command;
    private String name;

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
