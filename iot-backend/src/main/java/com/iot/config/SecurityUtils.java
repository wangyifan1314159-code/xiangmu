package com.iot.config;
// Spring Security 的上下文工具类（Security Helper / Facade），
// 作用是：从当前线程的安全上下文中提取登录用户信息，并进一步映射到数据库中的用户实体。
import com.iot.model.User;
import com.iot.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class SecurityUtils {

    private final UserRepository userRepository;

    public SecurityUtils(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** 获取当前登录用户的 ID */
    public Long getCurrentUserId() {
        String username = getCurrentUsername();
        if (username == null) return null;
        return userRepository.findByUsername(username)
                .map(User::getId)
                .orElse(null);
    }

    /** 获取当前登录用户的完整信息 */
    public User getCurrentUser() {
        String username = getCurrentUsername();
        if (username == null) return null;
        return userRepository.findByUsername(username).orElse(null);
    }

    /** 当前认证是否携带指定角色（如 "DEVICE"、"ADMIN"） */
    public boolean hasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        for (GrantedAuthority a : auth.getAuthorities()) {
            if (a.getAuthority().equals("ROLE_" + role)) return true;
        }
        return false;
    }

    /** 设备 API Key 认证时，返回认证密钥对应的 deviceId（由 DeviceApiKeyFilter 写入请求属性） */
    public String getAuthenticatedDeviceId() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            Object deviceId = attrs.getRequest().getAttribute("deviceId");
            return deviceId != null ? deviceId.toString() : null;
        }
        return null;
    }

    /** 从 SecurityContext 获取当前用户名 */
    public String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        return auth.getName();
    }
}
