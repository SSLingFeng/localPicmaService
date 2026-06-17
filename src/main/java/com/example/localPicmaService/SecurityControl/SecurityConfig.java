package com.example.localPicmaService.SecurityControl;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    // ★ 新增：把实例存为字段，同一个实例用两次
    private static final JwtFilter jwtFilter = new JwtFilter();

    @Bean
    public static SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/login",
                                "/home",
                                "/home/api/**",       // 首页数据接口（公开访问）
                                "/public/res/**",     // 公开静态资源（免登录）
                                "/apilogin",
                                "/apicheck-token",
                                "/health").permitAll()
                        .anyRequest().authenticated()
                )
                // ★ 加这个，控制台会打印每个请求的认证情况
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            System.out.println(">>> 403: " + request.getMethod() + " " + request.getRequestURI());
                            response.sendError(HttpServletResponse.SC_FORBIDDEN, "未认证");
                        })
                )
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

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

    // ★ 新增：把 JwtFilter 注册为 Bean，这样可以注入到 securityFilterChain
    @Bean
    public JwtFilter jwtFilter() {
        return jwtFilter;
    }

    @Autowired
    private CustomUserDetailsService customUserDetailsService;
}
