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
        // 仅在设备数据面路径上处理 API Key；其余路径直接放行，交由 JWT 过滤器与授权规则处理
        if (!isDeviceDataPath(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String apiKey = request.getHeader("X-Api-Key");
        // 未携带 API Key：回退到 JWT 认证（普通用户/管理员访问数据面）
        if (!StringUtils.hasText(apiKey)) {
            filterChain.doFilter(request, response);
            return;
        }

        Optional<Device> deviceOpt = deviceRepository.findByApiKey(apiKey);
        if (deviceOpt.isEmpty()) {
            // 携带了 API Key 但无效：直接 401，不再静默放行
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "无效的 API Key");
            return;
        }

        Device device = deviceOpt.get();
        Optional<User> userOpt = userRepository.findById(device.getOwnerId());
        if (userOpt.isEmpty()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "设备未绑定有效用户");
            return;
        }

        User user = userOpt.get();
        // principal 使用 owner 用户名，便于按 owner 解析数据归属；
        // 权限固定为 ROLE_DEVICE（最小权限，见 DataService.requireOwnedDevice）
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        user.getUsername(), null,
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_DEVICE")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        request.setAttribute("deviceId", device.getDeviceId());

        filterChain.doFilter(request, response);
    }

    /** 设备 API Key 仅允许访问 /api/data/** 数据面路径 */
    private boolean isDeviceDataPath(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/data/");
    }
}
