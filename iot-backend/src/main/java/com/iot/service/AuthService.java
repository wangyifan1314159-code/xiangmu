package com.iot.service;

import com.iot.config.JwtUtil;
import com.iot.dto.LoginRequest;
import com.iot.dto.RegisterRequest;
import com.iot.model.User;
import com.iot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
// 系统用户认证服务，实现两种登录方式
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Value("${app.auth.phone-login.enabled:true}")
    private boolean phoneLoginEnabled;

    // 内存验证码存储：电话->{代码，时间戳，剩余尝试次数}
    private final Map<String, String> codeStore = new ConcurrentHashMap<>();
    private final Map<String, Long> codeTimeStore = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> codeAttemptStore = new ConcurrentHashMap<>();
    private static final long CODE_EXPIRE_MS = 5 * 60 * 1000; // 5min
    private static final long CODE_RESEND_INTERVAL_MS = 60 * 1000; // 同号 60s 内限发一次
    private static final int CODE_MAX_ATTEMPTS = 5;               // 验证错 5 次作废
    private static final int MAX_CODE_STORE_SIZE = 1000;          // 防止无限制发送撑爆内存

    // 登录失败锁定：用户名 -> 失败次数 / 锁定截止时间
    private final Map<String, AtomicInteger> loginFailures = new ConcurrentHashMap<>();
    private final Map<String, Long> loginLockUntil = new ConcurrentHashMap<>();
    private static final int MAX_LOGIN_FAILURES = 5;
    private static final long LOGIN_LOCK_MS = 10 * 60 * 1000; // 锁 10 分钟

    private final SecureRandom secureRandom = new SecureRandom();

    public void sendVerificationCode(String phone) {
        if (!phoneLoginEnabled) {
            throw new RuntimeException("手机验证码登录未启用");
        }
        Long lastSent = codeTimeStore.get(phone);
        if (lastSent != null && System.currentTimeMillis() - lastSent < CODE_RESEND_INTERVAL_MS) {
            throw new RuntimeException("发送过于频繁，请稍后再试");
        }
        if (codeStore.size() > MAX_CODE_STORE_SIZE) {
            cleanupExpiredCodes();
        }
        String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        codeStore.put(phone, code);
        codeTimeStore.put(phone, System.currentTimeMillis());
        codeAttemptStore.put(phone, new AtomicInteger(0));
        // 未接入真实短信网关前仅输出脱敏提示；接入网关后此行应删除
        log.info("[验证码] 已为手机号 {} 生成验证码（详情不打印）", phone.substring(phone.length() - 4));
    }

    public String loginByPhone(String phone, String code) {
        if (!phoneLoginEnabled) {
            throw new RuntimeException("手机验证码登录未启用");
        }
        String savedCode = codeStore.get(phone);
        Long savedTime = codeTimeStore.get(phone);
        if (savedCode == null || savedTime == null) {
            throw new RuntimeException("请先获取验证码");
        }
        if (System.currentTimeMillis() - savedTime > CODE_EXPIRE_MS) {
            removeCode(phone);
            throw new RuntimeException("验证码已过期，请重新获取");
        }
        AtomicInteger attempts = codeAttemptStore.get(phone);
        if (attempts != null && attempts.incrementAndGet() > CODE_MAX_ATTEMPTS) {
            removeCode(phone);
            throw new RuntimeException("尝试次数过多，验证码已作废，请重新获取");
        }
        if (!savedCode.equals(code)) {
            throw new RuntimeException("验证码错误");
        }
        removeCode(phone);

        User user = userRepository.findByPhone(phone).orElseGet(() -> {
            // 自动注册的账号使用随机密码（该账号仅可通过手机验证码登录）
            User newUser = User.builder()
                    .username("用户" + phone.substring(phone.length() - 4))
                    .email(phone + "@iot.com")
                    .phone(phone)
                    .password(passwordEncoder.encode(randomToken()))
                    .role("USER")
                    .build();
            return userRepository.save(newUser);
        });

        return jwtUtil.generateToken(user.getUsername());
    }

    public User register(RegisterRequest request) {
        // 合并提示，避免通过不同报错枚举已注册的用户名/邮箱
        if (userRepository.existsByUsername(request.getUsername())
                || userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("用户名或邮箱已被注册");
        }
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role("USER")
                .build();
        return userRepository.save(user);
    }

    public String login(LoginRequest request) {
        String username = request.getUsername();

        // 失败锁定检查（防在线爆破）
        Long lockUntil = loginLockUntil.get(username);
        if (lockUntil != null) {
            if (System.currentTimeMillis() < lockUntil) {
                long remainMin = (lockUntil - System.currentTimeMillis()) / 60000 + 1;
                throw new RuntimeException("失败次数过多，账号已临时锁定，请约 " + remainMin + " 分钟后再试");
            }
            loginLockUntil.remove(username);
            loginFailures.remove(username);
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用户名或密码错误"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            recordLoginFailure(username);
            throw new RuntimeException("用户名或密码错误");
        }

        loginFailures.remove(username);
        loginLockUntil.remove(username);
        return jwtUtil.generateToken(user.getUsername());
    }

    private void recordLoginFailure(String username) {
        AtomicInteger failures = loginFailures.computeIfAbsent(username, k -> new AtomicInteger(0));
        if (failures.incrementAndGet() >= MAX_LOGIN_FAILURES) {
            loginLockUntil.put(username, System.currentTimeMillis() + LOGIN_LOCK_MS);
            log.warn("账号 {} 连续登录失败 {} 次，已临时锁定 {} 分钟", username, MAX_LOGIN_FAILURES, LOGIN_LOCK_MS / 60000);
        }
    }

    public User getCurrentUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
    }

    public User getCurrentUserByPhone(String phone) {
        return userRepository.findByPhone(phone)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + username));

        // 携带真实角色，供 hasRole/hasAnyRole 授权判断使用
        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole()))
        );
    }

    private void removeCode(String phone) {
        codeStore.remove(phone);
        codeTimeStore.remove(phone);
        codeAttemptStore.remove(phone);
    }

    private void cleanupExpiredCodes() {
        long now = System.currentTimeMillis();
        codeTimeStore.forEach((phone, ts) -> {
            if (now - ts > CODE_EXPIRE_MS) removeCode(phone);
        });
    }

    private String randomToken() {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
