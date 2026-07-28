package com.example.localPicmaService.api.command.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class CommandStartResponse {
    private String taskId;
    private String name;
    private String status;
}
