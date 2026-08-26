package com.iot.config;
// 启动时自动执行的初始化数据类
import com.iot.model.Device;
import com.iot.model.Sensor;
import com.iot.model.User;
import com.iot.repository.DeviceRepository;
import com.iot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed-data.enabled:true}")
    private boolean seedDataEnabled;

    /** 测试账号开关：每次启动确保 admin/user 账号存在（存在则不改动，即不会被删除） */
    @Value("${app.seed-users.enabled:true}")
    private boolean seedUsersEnabled;

    @Override
    @Transactional
    public void run(String... args) {
        // 测试账号：admin/admin123（ADMIN）、user/user123（USER）
        // 仅限本地/测试环境使用，生产环境必须设为 app.seed-users.enabled=false
        if (seedUsersEnabled) {
            System.out.println("[种子账号] app.seed-users.enabled=true，确保测试账号存在：admin/admin123（管理员）、user/user123（普通用户）");
            ensureUser("admin", "admin@iot.com", "admin123", "ADMIN");
            ensureUser("user",  "user@iot.com",  "user123",  "USER");
        }

        // 种子设备：由 app.seed-data.enabled 独立控制（deploy 模式默认关闭）
        if (seedDataEnabled) {
            userRepository.findByUsername("admin").ifPresent(u -> ensureSeedDevices(u.getId()));
            userRepository.findByUsername("user").ifPresent(u -> ensureSeedDevices(u.getId()));
        }
    }

    private User ensureUser(String username, String email, String password, String role) {
        return userRepository.findByUsername(username).orElseGet(() ->
            userRepository.save(User.builder()
                    .username(username)
                    .email(email)
                    .password(passwordEncoder.encode(password))
                    .role(role)
                    .build()));
    }

    private void ensureSeedDevices(Long ownerId) {
        if (deviceRepository.findAllByOwnerId(ownerId).isEmpty()) {
            String p = "s_" + ownerId + "_"; // prefix to make sensor IDs unique per user
            createDevice(ownerId, "dev_" + ownerId + "01", "温湿度传感器-A1", "温湿度传感器", "ONLINE",
                    "实验室1区", "监测实验室温湿度",
                    new String[][]{{p + "001", "温度", "temperature", "°C", "24.5", "-10", "60"},
                                   {p + "002", "湿度", "humidity", "%RH", "65", "0", "100"}});
            createDevice(ownerId, "dev_" + ownerId + "02", "光照传感器-B2", "光照传感器", "ONLINE",
                    "大棚区A", "农业大棚光照监测",
                    new String[][]{{p + "003", "光照强度", "light", "lux", "12500", "0", "100000"}});
            createDevice(ownerId, "dev_" + ownerId + "03", "空气质量监测-C1", "空气质量传感器", "WARNING",
                    "办公室", "室内空气质量监测",
                    new String[][]{{p + "004", "PM2.5", "pm25", "μg/m³", "35", "0", "500"},
                                   {p + "005", "CO2", "co2", "ppm", "800", "0", "5000"},
                                   {p + "006", "TVOC", "tvoc", "mg/m³", "0.5", "0", "10"}});
            createDevice(ownerId, "dev_" + ownerId + "04", "智能电表-D1", "电能监测", "OFFLINE",
                    "配电房", "配电房电能消耗监测",
                    new String[][]{{p + "007", "电压", "voltage", "V", "220", "0", "380"},
                                   {p + "008", "电流", "current", "A", "5.2", "0", "50"},
                                   {p + "009", "功率", "power", "W", "1144", "0", "10000"}});
        }
    }

    private void createDevice(Long ownerId, String deviceId, String name, String type,
                              String status, String location, String desc, String[][] sensorDefs) {
        Device device = Device.builder()
                .deviceId(deviceId)
                .name(name)
                .type(type)
                .status(status)
                .location(location)
                .description(desc)
                .ownerId(ownerId)
                .createdAt(LocalDateTime.now())
                .build();
        for (String[] s : sensorDefs) {
            device.addSensor(Sensor.builder()
                    .id(s[0]).name(s[1]).type(s[2]).unit(s[3])
                    .value(Double.parseDouble(s[4]))
                    .minVal(Double.parseDouble(s[5]))
                    .maxVal(Double.parseDouble(s[6]))
                    .device(device)
                    .build());
        }
        deviceRepository.save(device);
    }
}
