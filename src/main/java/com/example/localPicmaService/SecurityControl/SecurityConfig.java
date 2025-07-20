package com.example.localPicmaService.SecurityControl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.Customizer;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    public static SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // 禁用 CSRF（可选）
                .authorizeHttpRequests(auth -> auth
                                // 不需要认证的路径
                                .requestMatchers("/public/**", "/login", "/apilogin", "/health", "/DataControl/**").permitAll()
//                        .requestMatchers("/public/**", "/login", "/health","/api").permitAll()

                                // 需要认证的路径
                                .requestMatchers("/admin/**").hasRole("ADMIN")
                                .requestMatchers("/user/**").authenticated()

                                // 所有其他接口默认需要登录
                                .anyRequest().authenticated()
                )
                .formLogin(Customizer.withDefaults())  // 使用默认表单登录页
                .addFilterBefore(new JwtFilter(), UsernamePasswordAuthenticationFilter.class)
//                .formLogin(form -> form.disable())
//                .formLogin(form -> form
//                        .loginPage("/login") // 你可以自定义页面，也可不写使用默认登录页
//                        .permitAll()
//                )
        ;
        return http.build();
    }


    // ① 注册 AuthenticationManager 以便在 Controller 中使用
    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http) throws Exception {
        AuthenticationManagerBuilder builder = http.getSharedObject(AuthenticationManagerBuilder.class);
        builder
                .userDetailsService(customUserDetailsService)
                .passwordEncoder(PasswordConfig.passwordEncoder());
        return builder.build();
    }

    @Autowired
    private CustomUserDetailsService customUserDetailsService;
}
