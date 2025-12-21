package com.example.localPicmaService.Class.CommandPackeg.Class;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class CommandStartResponse {
    private String taskId;
    private String name;
    private String status;
}
