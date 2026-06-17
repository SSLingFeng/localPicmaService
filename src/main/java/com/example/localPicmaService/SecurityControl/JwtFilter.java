package com.example.localPicmaService.SecurityControl;

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
import org.springframework.stereotype.Component;
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

        // ★ 优先从 Header 读，其次从 Cookie 读
        String jwtStr = extractToken(request);
        // ★ 最开头加这行，确认 filter 是否执行
        System.out.println(">>> JwtFilter 执行: " + request.getMethod() + " " + request.getRequestURI());

        System.out.println(">>> Token 提取: " + (jwtStr != null ? "成功 [" + jwtStr.substring(0, 20) + "...]" : "无"));



        // 禁止浏览器发送 Referer 头
        response.setHeader("Referrer-Policy", "no-referrer");



        if (jwtStr != null) {
            try {
                JWT jwt = parseAndVerify(jwtStr);
//                if (!jwt.verify()) {
//                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
//                    return;
//                }
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


//                List<SimpleGrantedAuthority> authorities = roles.stream()
//                        .map(SimpleGrantedAuthority::new)
//                        .toList();

//                UsernamePasswordAuthenticationToken authentication =
//                        new UsernamePasswordAuthenticationToken(username, null, authorities);
//                SecurityContextHolder.getContext().setAuthentication(authentication);


            } catch (JWTException | ClassCastException e) {
                System.out.println(">>> Token 异常: " + e.getMessage());
            }
        }
        // ★ 无论 token 是否有效，都继续执行过滤链
        filterChain.doFilter(request, response);
    }

    /**
     * ★ 新增：按优先级提取 token
     *   1. Authorization: Bearer xxx（API 客户端用）
     *   2. Cookie: AUTH_TOKEN=xxx（浏览器导航时自动携带）
     */
    private String extractToken(HttpServletRequest request) {

        // 1. 先看 Authorization Header
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        // 2. 再看 Cookie
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
