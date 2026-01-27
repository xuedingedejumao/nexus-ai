package com.example.nexusai.controller;

import com.example.nexusai.dto.LoginRequest;
import com.example.nexusai.service.AuthService;
import lombok.RequiredArgsConstructor;
import
 org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Map<String, String> login(@RequestBody LoginRequest request) {
        String token = authService.login(request.getUsername(), request.getPassword());

        Map<String, String> response = new HashMap<>();
        response.put("token", token);
        return response;
    }

    @PostMapping("/register")
    public String register(@RequestBody LoginRequest request) {
        authService.register(request.getUsername(), request.getPassword());
        return "注册成功";
    }
}