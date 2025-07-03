package com.example.localPicmaService.SecurityControl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.Customizer;

@Configuration
public class SecurityConfig {

    @Bean
    public static SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // 禁用 CSRF（可选）
                .authorizeHttpRequests(auth -> auth
                        // 不需要认证的路径
                        .requestMatchers("/public/**", "/login", "/health","/api").permitAll()

                        // 需要认证的路径
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/user/**").authenticated()

                        // 所有其他接口默认需要登录
                        .anyRequest().authenticated()
                )
                .formLogin(Customizer.withDefaults()); // 使用默认表单登录页

        return http.build();
    }
}
