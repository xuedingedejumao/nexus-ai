package com.example.nexusai.filter;

import com.example.nexusai.utils.JwtUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 1. 获取 Authorization 头
        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String username;

        // 2. 如果没有头，或者不是以 Bearer 开头，直接放行（让后面的过滤器去处理，或者被拦截）
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 3. 提取 Token
        jwt = authHeader.substring(7); // 去掉 "Bearer "
        username = jwtUtils.extractUsername(jwt); // 从 Token 里拿用户名

        // 4. 如果用户名存在，且当前上下文没有认证信息（防止重复认证）
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            // 验证 Token 是否有效
            if (jwtUtils.validateToken(jwt, username)) {
                // 5. 生成认证对象 (这里暂时给个空权限列表 ArrayList，后面做角色权限时再改)
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        username,
                        null,
                        new ArrayList<>()
                );

                // 设置请求详情 (IP, SessionId 等)
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // 6. 将认证信息存入 Spring Security 上下文
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        // 7. 继续执行过滤器链
        filterChain.doFilter(request, response);
    }
}