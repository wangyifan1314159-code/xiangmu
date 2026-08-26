package com.iot.config;

import com.iot.model.User;
import com.iot.repository.DeviceRepository;
import com.iot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.security.Principal;
import java.util.List;
import java.util.regex.Pattern;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;

    private static final Pattern DEVICE_TOPIC_PATTERN = Pattern.compile("^/topic/device/([^/]+)$");

    /** 允许的来源白名单（默认前端开发服务器 5173 + 同源部署地址 8080），逗号分隔，可配置 app.cors.allowed-origins */
    @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:8080,http://127.0.0.1:5173}")
    private List<String> allowedOrigins;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 客户端订阅前缀：/topic（广播）、/queue（用户定向单播，配合 convertAndSendToUser）
        config.enableSimpleBroker("/topic", "/queue", "/user");
        // 服务端接收前缀
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // SockJS 端点，前端连接地址
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOrigins.toArray(new String[0]))
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // STOMP CONNECT 帧强制携带有效 JWT，匿名连接一律拒绝
        // （HTTP 握手层 /ws/** 仍为 permitAll，认证在此处完成）
        // SUBSCRIBE 帧校验设备归属，防止跨用户订阅他人设备遥测
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                        message, StompHeaderAccessor.class);
                if (accessor == null) {
                    return message;
                }
                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    handleConnect(accessor);
                } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    handleSubscribe(accessor);
                }
                return message;
            }

            private void handleConnect(StompHeaderAccessor accessor) {
                String bearer = accessor.getFirstNativeHeader("Authorization");
                String token = (bearer != null && bearer.startsWith("Bearer "))
                        ? bearer.substring(7) : null;
                if (!StringUtils.hasText(token) || !jwtUtil.validateToken(token)) {
                    throw new IllegalArgumentException("WebSocket 连接未认证或令牌无效");
                }
                String username = jwtUtil.getUsernameFromToken(token);
                User user = userRepository.findByUsername(username).orElse(null);
                if (user == null) {
                    throw new IllegalArgumentException("WebSocket 连接用户不存在");
                }
                accessor.setUser((Principal) () -> username);
                log.debug("WebSocket CONNECT authenticated for user {}", username);
            }

            private void handleSubscribe(StompHeaderAccessor accessor) {
                String destination = accessor.getDestination();
                Principal user = accessor.getUser();
                if (destination == null || user == null) {
                    throw new IllegalArgumentException("WebSocket 订阅未认证");
                }
                User u = userRepository.findByUsername(user.getName()).orElse(null);
                boolean isAdmin = u != null && "ADMIN".equalsIgnoreCase(u.getRole());

                // 用户定向队列（/user/queue/alert 等）：由 convertAndSendToUser 定向投递，
                // 订阅者只能收到自己的消息，放行即可
                if (destination.startsWith("/user/")) {
                    return;
                }

                // 全局广播主题：告警/摘要仅管理员可订阅，防止普通用户跨租户监听他人告警
                if ("/topic/alert".equals(destination) || "/topic/summary".equals(destination)) {
                    if (!isAdmin) {
                        log.warn("WebSocket subscribe denied: user={} tried global topic {}", user.getName(), destination);
                        throw new IllegalArgumentException("无权订阅全局告警/摘要");
                    }
                    return;
                }

                java.util.regex.Matcher matcher = DEVICE_TOPIC_PATTERN.matcher(destination);
                if (matcher.matches()) {
                    String deviceId = matcher.group(1);
                    boolean isOwner = u != null && deviceRepository
                            .findByDeviceIdAndOwnerId(deviceId, u.getId()).isPresent();
                    if (!isAdmin && !isOwner) {
                        log.warn("WebSocket subscribe denied: user={} tried device={}",
                                user.getName(), deviceId);
                        throw new IllegalArgumentException("无权订阅该设备的数据");
                    }
                }
            }
        });
    }
}
