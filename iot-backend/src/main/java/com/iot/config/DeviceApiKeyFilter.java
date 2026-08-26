package com.iot.config;
// Spring Security 自定义一次性请求过滤器，用于物联网设备通过 API 密钥免账号密码鉴权上报数据
import com.iot.model.Device;
import com.iot.model.User;
import com.iot.repository.DeviceRepository;
import com.iot.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

public class DeviceApiKeyFilter extends OncePerRequestFilter {

    private final DeviceRepository deviceRepository;
    private final UserRepository userRepository;

    public DeviceApiKeyFilter(DeviceRepository deviceRepository, UserRepository userRepository) {
        this.deviceRepository = deviceRepository;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String apiKey = request.getHeader("X-Api-Key");

        if (StringUtils.hasText(apiKey)) {
            // 设备密钥只授予数据面权限（遥测上报/查询），不得以 owner 身份访问其余 API
            if (!request.getRequestURI().startsWith("/api/data/")) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                        "API Key 仅允许访问 /api/data/** 路径");
                return;
            }
            Optional<Device> deviceOpt = deviceRepository.findByApiKey(apiKey);
            if (deviceOpt.isPresent()) {
                Device device = deviceOpt.get();
                Optional<User> userOpt = userRepository.findById(device.getOwnerId());
                if (userOpt.isPresent()) {
                    User user = userOpt.get();
                    // principal 使用 owner 用户名，便于按 owner 解析数据归属；
                    // 权限固定为 ROLE_DEVICE（最小权限，见 DataService.requireOwnedDevice）
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    user.getUsername(), null,
                                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_DEVICE")));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    request.setAttribute("deviceId", device.getDeviceId());
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
