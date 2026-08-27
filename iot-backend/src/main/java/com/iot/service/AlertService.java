package com.iot.service;

import com.iot.config.SecurityUtils;
import com.iot.model.*;
import com.iot.repository.AlertRecordRepository;
import com.iot.repository.AlertRuleRepository;
import com.iot.repository.DeviceRepository;
import com.iot.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
// 告警体系的核心业务逻辑
@Service
@Slf4j
public class AlertService {

    private final AlertRuleRepository ruleRepository;
    private final AlertRecordRepository recordRepository;
    private final DeviceRepository deviceRepository;
    private final UserRepository userRepository;
    private final SecurityUtils securityUtils;

    public AlertService(AlertRuleRepository ruleRepository,
                        AlertRecordRepository recordRepository,
                        DeviceRepository deviceRepository,
                        UserRepository userRepository,
                        SecurityUtils securityUtils) {
        this.ruleRepository = ruleRepository;
        this.recordRepository = recordRepository;
        this.deviceRepository = deviceRepository;
        this.userRepository = userRepository;
        this.securityUtils = securityUtils;
    }

    private Long currentUserId() {
        Long id = securityUtils.getCurrentUserId();
        if (id == null) throw new RuntimeException("用户未登录");
        return id;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private RedisCacheService redis;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private WebSocketPushService wsPush;

    // ========== 规则管理（全部按归属过滤，规则不存在与无权访问返回同一报错） ==========

    public List<AlertRule> getAllRules() {
        return ruleRepository.findAllByOwnerId(currentUserId());
    }

    public AlertRule getRule(Long id) {
        return ruleRepository.findByIdAndOwnerId(id, currentUserId())
                .orElseThrow(() -> new RuntimeException("告警规则不存在: " + id));
    }

    @Transactional
    public AlertRule createRule(AlertRule rule) {
        rule.setOwnerId(currentUserId());
        rule.setUpdatedAt(LocalDateTime.now());
        return ruleRepository.save(rule);
    }

    @Transactional
    public AlertRule updateRule(Long id, AlertRule updates) {
        AlertRule rule = getRule(id);
        rule.setName(updates.getName());
        rule.setConditionExpr(updates.getConditionExpr());
        rule.setLevel(updates.getLevel());
        rule.setDebounceSeconds(updates.getDebounceSeconds());
        rule.setEnabled(updates.isEnabled());
        rule.setNotifyEmail(updates.isNotifyEmail());
        rule.setNotifySms(updates.isNotifySms());
        rule.setNotifyPush(updates.isNotifyPush());
        rule.setDescription(updates.getDescription());
        rule.setSensorType(updates.getSensorType());
        rule.setDeviceId(updates.getDeviceId());
        rule.setProductType(updates.getProductType());
        rule.setUpdatedAt(LocalDateTime.now());
        return ruleRepository.save(rule);
    }

    @Transactional
    public void deleteRule(Long id) {
        AlertRule rule = getRule(id);
        ruleRepository.delete(rule);
    }

    @Transactional
    public AlertRule toggleRule(Long id, boolean enabled) {
        AlertRule rule = getRule(id);
        rule.setEnabled(enabled);
        rule.setUpdatedAt(LocalDateTime.now());
        return ruleRepository.save(rule);
    }

    // ========== 告警记录查询 ==========

    public Page<AlertRecord> getAlertRecords(String deviceId, String status,
                                              String level, Pageable pageable) {
        return recordRepository.search(deviceId, status, level, currentUserId(), pageable);
    }

    /** 内部调用：按 ownerId 查询（供后台任务使用） */
    public Page<AlertRecord> getAlertRecordsByOwner(String deviceId, String status,
                                                     String level, Long ownerId, Pageable pageable) {
        return recordRepository.search(deviceId, status, level, ownerId, pageable);
    }

    public AlertRecord getAlertRecord(Long id) {
        AlertRecord record = recordRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("告警记录不存在: " + id));
        if (!record.getOwnerId().equals(currentUserId())) {
            throw new RuntimeException("告警记录不存在: " + id);
        }
        return record;
    }

    @Transactional
    public AlertRecord acknowledgeAlert(Long id) {
        AlertRecord record = getAlertRecord(id);
        record.setStatus("ACKNOWLEDGED");
        record.setAcknowledgedAt(LocalDateTime.now());
        return recordRepository.save(record);
    }

    @Transactional
    public AlertRecord resolveAlert(Long id) {
        AlertRecord record = getAlertRecord(id);
        record.setStatus("RESOLVED");
        record.setResolvedAt(LocalDateTime.now());
        return recordRepository.save(record);
    }

    public Map<String, Long> getAlertStats() {
        Long uid = currentUserId();
        return Map.of(
                "total", recordRepository.countByOwnerId(uid),
                "active", recordRepository.countActiveAlerts(uid),
                "acknowledged", recordRepository.countByStatus("ACKNOWLEDGED", uid),
                "resolved", recordRepository.countByStatus("RESOLVED", uid)
        );
    }

    @Transactional
    public void deleteAlert(Long id) {
        AlertRecord record = getAlertRecord(id);
        recordRepository.delete(record);
    }

    // ========== 核心告警评估引擎 ==========

    /**
     * 对一条设备数据评估所有匹配的告警规则
     * 被 DataService 和 KafkaConsumerService 调用
     */
    @Transactional
    public void evaluate(String deviceId, String sensorType, double value, String deviceType) {
        Device device = deviceRepository.findByDeviceId(deviceId).orElse(null);
        if (device == null) return;

        List<AlertRule> rules = findMatchingRules(deviceId, sensorType, deviceType);
        if (rules.isEmpty()) return;

        for (AlertRule rule : rules) {
            try {
                if (evaluateCondition(rule.getConditionExpr(), value)) {
                    // Redis 防抖检查（Redis 不可用时跳过防抖，直接触发告警）
                    boolean shouldTrigger = redis == null
                            || redis.checkAlertDebounce(device.getId(), rule.getId(), rule.getDebounceSeconds());
                    if (shouldTrigger) {

                        String title = buildAlertTitle(rule, device, sensorType, value);
                        String detail = buildAlertDetail(device, sensorType, value, rule);

                        AlertRecord record = AlertRecord.builder()
                                .deviceId(deviceId)
                                .deviceName(device.getName())
                                .ruleId(rule.getId())
                                .ruleName(rule.getName())
                                .level(rule.getLevel())
                                .title(title)
                                .detail(detail)
                                .ownerId(device.getOwnerId())
                                .build();

                        recordRepository.save(record);
                        log.info("ALERT triggered: {} [{}] device={} sensor={} value={}",
                                rule.getName(), rule.getLevel(), deviceId, sensorType, value);
                        if (wsPush != null) {
                            // 定向推送给设备归属用户，避免全局广播导致跨用户告警泄露
                            userRepository.findById(device.getOwnerId())
                                    .ifPresent(owner -> wsPush.pushAlert(
                                            owner.getUsername(), deviceId, rule.getLevel(), title));
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Failed to evaluate rule {} for device {}", rule.getId(), deviceId, e);
            }
        }
    }

    private List<AlertRule> findMatchingRules(String deviceId, String sensorType, String deviceType) {
        Device device = deviceRepository.findByDeviceId(deviceId).orElse(null);
        if (device == null) return List.of();
        Long ownerId = device.getOwnerId();

        // 精确匹配设备ID（仅该设备归属人创建的规则）
        List<AlertRule> deviceRules = ruleRepository.findByDeviceIdAndEnabledTrueAndOwnerId(deviceId, ownerId).stream()
                .filter(r -> r.getSensorType() == null || sensorType == null
                        || r.getSensorType().equals(sensorType))
                .toList();

        // 按产品类型匹配（同样仅限归属人规则；与设备级规则合并，不短路，保证两类规则都生效）
        List<AlertRule> productRules = List.of();
        if (deviceType != null) {
            productRules = ruleRepository.findByProductTypeAndDeviceIdIsNullAndEnabledTrueAndOwnerId(deviceType, ownerId).stream()
                    .filter(r -> r.getSensorType() == null || sensorType == null
                            || r.getSensorType().equals(sensorType))
                    .toList();
        }

        if (deviceRules.isEmpty() && productRules.isEmpty()) return List.of();
        return java.util.stream.Stream.concat(deviceRules.stream(), productRules.stream()).toList();
    }

    /**
     * 条件表达式求值（受限解析，仅支持 value 与数字的比较 + 布尔组合）
     * 支持比较运算符: &gt; &lt; &gt;= &lt;= == !=
     * 支持逻辑组合: &amp;&amp; || 以及括号分组，例如 "value &gt; 80 &amp;&amp; value &lt; 100"
     * 注意：不得引入通用脚本引擎求值——表达式来自用户输入，ScriptEngine 等于开放代码执行，
     * 这里使用无副作用的递归下降解析器，语法非法时抛出 IllegalArgumentException。
     */
    private boolean evaluateCondition(String expr, double value) {
        return simpleEvaluate(expr, value);
    }

    private boolean simpleEvaluate(String expr, double value) {
        if (expr == null || expr.isBlank()) {
            throw new IllegalArgumentException("告警条件表达式不能为空");
        }
        return new ConditionEvaluator(expr, value).evaluate();
    }

    /** 受限布尔表达式求值器（递归下降），无脚本执行、无外部依赖 */
    private static final class ConditionEvaluator {
        private final String input;
        private final double value;
        private int pos = 0;

        ConditionEvaluator(String input, double value) {
            this.input = input;
            this.value = value;
        }

        boolean evaluate() {
            boolean result = parseOr();
            skipWhitespace();
            if (pos < input.length()) {
                throw new IllegalArgumentException("告警条件表达式存在无法解析的内容: " + input);
            }
            return result;
        }

        // orExpr := andExpr ( "||" andExpr )*
        private boolean parseOr() {
            boolean left = parseAnd();
            while (true) {
                skipWhitespace();
                if (match("||")) {
                    boolean right = parseAnd();
                    left = left || right;
                } else {
                    return left;
                }
            }
        }

        // andExpr := unary ( "&&" unary )*
        private boolean parseAnd() {
            boolean left = parseUnary();
            while (true) {
                skipWhitespace();
                if (match("&&")) {
                    boolean right = parseUnary();
                    left = left && right;
                } else {
                    return left;
                }
            }
        }

        // unary := '(' orExpr ')' | comparison
        private boolean parseUnary() {
            skipWhitespace();
            if (match("(")) {
                boolean inner = parseOr();
                skipWhitespace();
                if (!match(")")) {
                    throw new IllegalArgumentException("告警条件表达式括号不匹配: " + input);
                }
                return inner;
            }
            return parseComparison();
        }

        // comparison := numeric op numeric
        private boolean parseComparison() {
            double lhs = parseNumeric();
            String op = readOperator();
            double rhs = parseNumeric();
            switch (op) {
                case ">":  return lhs > rhs;
                case "<":  return lhs < rhs;
                case ">=": return lhs >= rhs;
                case "<=": return lhs <= rhs;
                case "==": return Math.abs(lhs - rhs) < 1e-9;
                case "!=": return Math.abs(lhs - rhs) >= 1e-9;
                default: throw new IllegalArgumentException("不支持的比较运算符: " + op);
            }
        }

        // numeric := 'value' | number | '(' numeric ')'
        private double parseNumeric() {
            skipWhitespace();
            if (match("value")) {
                return value;
            }
            if (match("(")) {
                double n = parseNumeric();
                skipWhitespace();
                if (!match(")")) {
                    throw new IllegalArgumentException("告警条件表达式括号不匹配: " + input);
                }
                return n;
            }
            return readNumber();
        }

        private String readOperator() {
            skipWhitespace();
            if (input.startsWith(">=", pos)) { pos += 2; return ">="; }
            if (input.startsWith("<=", pos)) { pos += 2; return "<="; }
            if (input.startsWith("!=", pos)) { pos += 2; return "!="; }
            if (input.startsWith("==", pos)) { pos += 2; return "=="; }
            if (input.startsWith(">", pos)) { pos += 1; return ">"; }
            if (input.startsWith("<", pos)) { pos += 1; return "<"; }
            throw new IllegalArgumentException("告警条件表达式缺少比较运算符: " + input);
        }

        private double readNumber() {
            skipWhitespace();
            int start = pos;
            if (pos < input.length() && (input.charAt(pos) == '+' || input.charAt(pos) == '-')) {
                pos++;
            }
            boolean hasDigit = false;
            while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
                pos++;
                hasDigit = true;
            }
            if (pos < input.length() && input.charAt(pos) == '.') {
                pos++;
                while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
                    pos++;
                    hasDigit = true;
                }
            }
            if (!hasDigit) {
                throw new IllegalArgumentException("告警条件表达式中存在无效数字: " + input);
            }
            return Double.parseDouble(input.substring(start, pos));
        }

        private void skipWhitespace() {
            while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
                pos++;
            }
        }

        private boolean match(String token) {
            if (input.startsWith(token, pos)) {
                pos += token.length();
                return true;
            }
            return false;
        }
    }

    private String buildAlertTitle(AlertRule rule, Device device, String sensorType, double value) {
        return String.format("[%s] %s - %s: %.2f (阈值: %s)",
                rule.getLevel(), device.getName(), sensorType, value, rule.getConditionExpr());
    }

    private String buildAlertDetail(Device device, String sensorType, double value, AlertRule rule) {
        return String.format(
                "{\"deviceId\":\"%s\",\"deviceName\":\"%s\",\"sensorType\":\"%s\",\"value\":%.2f,\"condition\":\"%s\",\"level\":\"%s\",\"ruleId\":%d}",
                device.getDeviceId(), device.getName(), sensorType, value,
                rule.getConditionExpr(), rule.getLevel(), rule.getId());
    }
}
