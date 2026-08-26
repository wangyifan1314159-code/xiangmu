package com.iot.controller;

import com.iot.dto.ApiResponse;
import com.iot.dto.LoginByPhoneRequest;
import com.iot.dto.LoginRequest;
import com.iot.dto.RegisterRequest;
import com.iot.dto.SendCodeRequest;
import com.iot.model.User;
import com.iot.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        String token = authService.login(request);
        User user = authService.getCurrentUser(request.getUsername());
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("user", Map.of(
            "id", user.getId(),
            "username", user.getUsername(),
            "email", user.getEmail(),
            "phone", user.getPhone() != null ? user.getPhone() : "",
            "role", user.getRole(),
            "createdAt", user.getCreatedAt()
        ));
        return ApiResponse.ok("登录成功", result);
    }

    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request) {
        User user = authService.register(request);
        Map<String, Object> result = Map.of(
            "id", user.getId(),
            "username", user.getUsername(),
            "email", user.getEmail()
        );
        return ApiResponse.ok("注册成功", result);
    }

    @PostMapping("/send-code")
    public ApiResponse<Map<String, Object>> sendCode(@Valid @RequestBody SendCodeRequest request) {
        authService.sendVerificationCode(request.getPhone());
        return ApiResponse.ok("验证码已发送", null);
    }

    @PostMapping("/login-by-phone")
    public ApiResponse<Map<String, Object>> loginByPhone(@Valid @RequestBody LoginByPhoneRequest request) {
        String token = authService.loginByPhone(request.getPhone(), request.getCode());
        User user = authService.getCurrentUserByPhone(request.getPhone());
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("user", Map.of(
            "id", user.getId(),
            "username", user.getUsername(),
            "email", user.getEmail(),
            "phone", user.getPhone() != null ? user.getPhone() : "",
            "role", user.getRole(),
            "createdAt", user.getCreatedAt()
        ));
        return ApiResponse.ok("登录成功", result);
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> currentUser(Principal principal) {
        User user = authService.getCurrentUser(principal.getName());
        Map<String, Object> result = Map.of(
            "id", user.getId(),
            "username", user.getUsername(),
            "email", user.getEmail(),
            "phone", user.getPhone() != null ? user.getPhone() : "",
            "role", user.getRole(),
            "createdAt", user.getCreatedAt()
        );
        return ApiResponse.ok(result);
    }
}
