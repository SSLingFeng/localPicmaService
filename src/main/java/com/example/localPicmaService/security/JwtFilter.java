package com.example.localPicmaService.security;

import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTException;
import cn.hutool.jwt.JWTUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class JwtFilter extends OncePerRequestFilter {

    private static final String SECRET = "localPicmaService";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String jwtStr = extractToken(request);
        System.out.println(">>> JwtFilter 执行: " + request.getMethod() + " " + request.getRequestURI());
        System.out.println(">>> Token 提取: " + (jwtStr != null ? "成功 [" + jwtStr.substring(0, 20) + "...]" : "无"));

        response.setHeader("Referrer-Policy", "no-referrer");

        if (jwtStr != null) {
            try {
                JWT jwt = parseAndVerify(jwtStr);
                boolean verified = jwt.verify();
                System.out.println(">>> 签名验证: " + verified);
                String username = (String) jwt.getPayload("username");
                List<String> roles = (List<String>) jwt.getPayload("roles");

                if (username != null && roles != null) {
                    List<SimpleGrantedAuthority> authorities = roles.stream()
                            .map(SimpleGrantedAuthority::new)
                            .toList();
                    System.out.println(">>> 认证成功: " + username + " " + roles);
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(username, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (JWTException | ClassCastException e) {
                System.out.println(">>> Token 异常: " + e.getMessage());
            }
        }
        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("AUTH_TOKEN".equals(cookie.getName())) {
                    String value = cookie.getValue();
                    if (value != null && !value.isEmpty()) {
                        return value;
                    }
                }
            }
        }
        return null;
    }

    public static JWT parseAndVerify(String token) {
        JWT jwt = JWTUtil.parseToken(token).setKey(SECRET.getBytes());
        if (!jwt.verify()) throw new JWTException("无效Token");
        return jwt;
    }

    public static String createToken(Map<String, Object> payload) {
        return JWTUtil.createToken(payload, SECRET.getBytes());
    }
}
