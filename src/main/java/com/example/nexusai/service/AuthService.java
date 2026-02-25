package com.example.nexusai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.nexusai.common.exception.NexusException;
import com.example.nexusai.entity.User;
import com.example.nexusai.mapper.UserMapper;
import com.example.nexusai.utils.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    /**
     * 登录认证，校验通过后签发 JWT Token。
     * 无论用户名不存在还是密码错误，均返回统一提示语，防止用户枚举攻击。
     */
    public String login(String username, String password) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));

        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new NexusException(401, "用户名或密码错误");
        }

        return jwtUtils.generateToken(username);
    }

    public void register(String username, String password) {
        User exists = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (exists != null) {
            throw new NexusException(400, "用户名已存在");
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole("USER");

        userMapper.insert(user);
    }
}