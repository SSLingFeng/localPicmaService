package com.example.localPicmaService.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.example.localPicmaService.config.PasswordConfig;
import java.util.function.Supplier;

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
                                "/page/login/api/login",
                                "/page/login/api/check-token",
                                "/page/login/api/register",
                                "/page/login/api/migrate-passwords",
                                "/page/cartoon/api/cover",
                                "/page/cartoon/api/pageImage",
                                "/page/rustfs/api/download",
                                "/api/squad/**",
                                "/health").permitAll()
                        .anyRequest().access(superAdminOrAuthenticated())
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

    /**
     * 超级管理员（SSLingFengDev）或已认证用户均可通过。
     * 超管角色由 JwtFilter 自动注入 ROLE_SUPER_ADMIN 权限标识。
     */
    private static AuthorizationManager<RequestAuthorizationContext> superAdminOrAuthenticated() {
        return new AuthorizationManager<>() {
            @Override
            public AuthorizationDecision authorize(Supplier<? extends Authentication> supplier, RequestAuthorizationContext context) {
                Authentication auth = supplier.get();
                if (auth == null || !auth.isAuthenticated()
                        || "anonymousUser".equals(auth.getPrincipal())) {
                    return new AuthorizationDecision(false);
                }
                // 超管始终放行
                boolean isSuperAdmin = auth.getAuthorities().stream()
                        .anyMatch(a -> JwtFilter.SUPER_ADMIN_AUTH.equals(a.getAuthority()));
                if (isSuperAdmin) return new AuthorizationDecision(true);
                // 普通已认证用户
                return new AuthorizationDecision(true);
            }
        };
    }

    @Autowired
    private CustomUserDetailsService customUserDetailsService;
}
