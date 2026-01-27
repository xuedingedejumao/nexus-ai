package com.example.nexusai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
     * 登录逻辑
     * @return 生成的 JWT Token
     */
    public String login(String username, String password) {
        // 1. 查询用户
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username)
        );

        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        // 2. 校验密码 (注意：第一个参数是明文，第二个是数据库里的密文)
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("密码错误");
        }

        // 3. 生成 Token
        return jwtUtils.generateToken(username);
    }

    public void register(String username, String password) {
        // 1. 检查用户是否已存在
        User exists = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username)
        );
        if (exists != null) {
            throw new RuntimeException("用户名已存在");
        }

        // 2. 创建新用户
        User user = new User();
        user.setUsername(username);
        // 🔑 关键点：一定要用 passwordEncoder 加密后再存
        user.setPassword(passwordEncoder.encode(password));
        user.setRole("USER");

        userMapper.insert(user);
    }
}