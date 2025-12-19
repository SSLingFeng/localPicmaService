package com.example.localPicmaService.SecurityControl;

import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTException;
import cn.hutool.jwt.JWTUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Component
public class JwtFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String jwtStr = authHeader.substring(7);
            try {
                JWT jwt = parseAndVerify(jwtStr);
                // 校验签名是否合法
                if (!jwt.verify()) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    return;
                }

                // 提取用户名和角色
                String username = (String) jwt.getPayload("username");
                List<String> roles = (List<String>) jwt.getPayload("roles");

                List<SimpleGrantedAuthority> authorities = roles.stream()
                        .map(SimpleGrantedAuthority::new)
                        .toList();

                // 设置认证对象
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(username, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);

            } catch (JWTException | ClassCastException e) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }

        }
//        else if (!request.getRequestURI().contains("/apilogin")) {
//            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
//            return;
//        }

        // 放行
        filterChain.doFilter(request, response);
    }

    private static final String SECRET = "localPicmaService";

    public static JWT parseAndVerify(String token) {
        JWT jwt = JWTUtil.parseToken(token).setKey(SECRET.getBytes());
        if (!jwt.verify()) throw new JWTException("无效Token");
        return jwt;
    }

    public static String createToken(Map<String, Object> payload) {
        return JWTUtil.createToken(payload, SECRET.getBytes());
    }
}
