package com.example.nexusai.utils;

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

    public Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        String username = authentication.getName();

        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User> query = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        query.eq(User::getUsername, username);
        query.select(User::getId);
        User user = userMapper.selectOne(query);
        return user != null ? user.getId() : null;
    }
}
