package com.example.localPicmaService.SecurityControl;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return NoOpPasswordEncoder.getInstance(); // 明文密码比对
//        return new BCryptPasswordEncoder();  // 密文密码比对  数据库密码列必须是   BCrypt加密后的
    }
}
