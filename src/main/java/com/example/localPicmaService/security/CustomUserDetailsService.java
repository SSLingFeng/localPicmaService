package com.example.localPicmaService.security;

import com.example.localPicmaService.tool.SQLTool.SqlUtil;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String sql = "SELECT user_name, password, role, enabled FROM web_user WHERE user_name = {?varchar|username?}";
        List<Map<String, Object>> users;
        try {
            users = SqlUtil.query(sql, Map.of("username", username), 1);
        } catch (Exception e) {
            throw new UsernameNotFoundException("查询用户失败: " + e.getMessage());
        }
        if (users.isEmpty()) throw new UsernameNotFoundException("用户不存在: " + username);

        Map<String, Object> user = users.get(0);
        String dbUsername = (String) user.get("user_name");
        String dbPassword = (String) user.get("password");
        String role = (String) user.get("role");
        Boolean enabled = user.get("enabled").equals(1);
        UserDetails userDetails = new org.springframework.security.core.userdetails.User(
                dbUsername, dbPassword, enabled, true, true, true,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        return userDetails;
    }
}
