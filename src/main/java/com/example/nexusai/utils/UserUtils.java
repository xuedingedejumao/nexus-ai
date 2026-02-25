package com.example.nexusai.utils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.nexusai.entity.User;
import com.example.nexusai.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserUtils {

    private final UserMapper userMapper;

    /**
     * 从当前 SecurityContext 中解析已认证用户的数据库主键 ID。
     * 返回 null 表示当前请求未通过认证（匿名访问）。
     */
    public Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        String username = authentication.getName();

        LambdaQueryWrapper<User> query = new LambdaQueryWrapper<>();
        query.eq(User::getUsername, username);
        query.select(User::getId);
        User user = userMapper.selectOne(query);
        return user != null ? user.getId() : null;
    }
}
