package com.example.nexusai.controller;

import com.example.nexusai.common.result.Result;
import com.example.nexusai.dto.LoginRequest;
import com.example.nexusai.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Result<Map<String, String>> login(@Valid @RequestBody LoginRequest request) {
        String token = authService.login(request.getUsername(), request.getPassword());
        return Result.success(Map.of("token", token));
    }

    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody LoginRequest request) {
        authService.register(request.getUsername(), request.getPassword());
        return Result.success();
    }
}