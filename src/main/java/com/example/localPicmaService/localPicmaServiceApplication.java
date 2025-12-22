package com.example.localPicmaService;

import com.example.localPicmaService.base.DataSourceControl;
import com.example.localPicmaService.base.SystemConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class localPicmaServiceApplication {

    public static void main(String[] args) {

        SystemConfig.init();


        SpringApplication.run(localPicmaServiceApplication.class, args);

    }

}
