package com.example.localPicmaService;

import com.example.localPicmaService.base.test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class localPicmaServiceApplication {

    public static void main(String[] args) {

        SpringApplication.run(localPicmaServiceApplication.class, args);
        test.main(null);
    }

}
