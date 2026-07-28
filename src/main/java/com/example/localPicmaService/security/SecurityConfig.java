package com.example.localPicmaService.security;

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

import com.example.localPicmaService.config.PasswordConfig;

@Configuration
public class SecurityConfig {

    private static final JwtFilter jwtFilter = new JwtFilter();

    @Bean
    public static SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/login",
                                "/register",
                                "/home",
                                "/home/api/**",
                                "/public/res/**",
                                "/apilogin",
                                "/apicheck-token",
                                "/api/register",
                                "/api/migrate-passwords",
                                "/health").permitAll()
                        .anyRequest().authenticated()
                )
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

    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http) throws Exception {
        AuthenticationManagerBuilder builder = http.getSharedObject(AuthenticationManagerBuilder.class);
        builder
                .userDetailsService(customUserDetailsService)
                .passwordEncoder(PasswordConfig.passwordEncoder());
        return builder.build();
    }

    @Bean
    public JwtFilter jwtFilter() {
        return jwtFilter;
    }

    @Autowired
    private CustomUserDetailsService customUserDetailsService;
}
