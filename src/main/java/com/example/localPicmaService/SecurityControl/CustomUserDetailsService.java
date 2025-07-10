package com.example.localPicmaService.SecurityControl;

import cn.hutool.json.JSONArray;
import com.example.localPicmaService.base.DataSourceControl;
import com.example.localPicmaService.base.DramVariable;
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
        // 使用你自己的查询工具（例如PgSqlUtil.query）
        DramVariable.set("username", username);
        String sql = "SELECT user_name, password, role, enabled FROM web_user WHERE user_name = {?username?}";
        JSONArray users = DataSourceControl.runQuery(sql);
        if (users.isEmpty()) throw new UsernameNotFoundException("用户不存在: " + username);

        Map<String, Object> user = (Map<String, Object>) users.get(0);
        String dbUsername = (String) user.get("user_name");
        String dbPassword = (String) user.get("password");
        String role = (String) user.get("role"); // e.g. USER
        Boolean enabled =   user.get("enabled").equals(1);
        UserDetails userDetails = new org.springframework.security.core.userdetails.User(
                dbUsername,
                dbPassword,
                enabled,
                true, true, true,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        return userDetails;
    }
}
