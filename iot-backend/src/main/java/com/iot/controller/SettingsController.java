package com.iot.controller;
// 通过 userId 实现数据隔离，实现用户个性化 settings 管理能力。
import com.iot.dto.ApiResponse;
import com.iot.model.SystemSettings;
import com.iot.model.User;
import com.iot.service.AuthService;
import com.iot.service.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;
    private final AuthService authService;

    @GetMapping
    public ApiResponse<SystemSettings> getSettings(Principal principal) {
        User user = authService.getCurrentUser(principal.getName());
        return ApiResponse.ok(settingsService.getSettings(user.getId()));
    }

    @PutMapping
    public ApiResponse<SystemSettings> updateSettings(Principal principal,
                                                       @RequestBody Map<String, Object> updates) {
        User user = authService.getCurrentUser(principal.getName());
        return ApiResponse.ok("设置已保存", settingsService.updateSettings(user.getId(), updates));
    }
}
