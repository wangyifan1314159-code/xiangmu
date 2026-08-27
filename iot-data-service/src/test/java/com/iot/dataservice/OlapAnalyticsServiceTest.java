package com.iot.dataservice;

import com.iot.dataservice.service.OlapAnalyticsService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

public class OlapAnalyticsServiceTest {

    private final OlapAnalyticsService service = new OlapAnalyticsService();

    @Test
    public void testQueryMetricSummary() {
        // metric-summary 尚未接入真实 Doris：应显式抛出 501 而非返回伪造数据
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.queryMetricSummary("INDUSTRIAL_GATEWAY", "1h", "7d"));
        assertEquals(501, ex.getStatusCode().value());
    }

    @Test
    public void testQueryDeviceHealthScores() {
        List<Map<String, Object>> healthList = service.queryDeviceHealthScores();
        assertNotNull(healthList);
        assertEquals(4, healthList.size());
        assertTrue(healthList.stream().anyMatch(d -> "DEV-LIGHT-001".equals(d.get("deviceId"))));
    }

    @Test
    public void testQueryScreenOverview() {
        Map<String, Object> overview = service.queryScreenOverview();
        assertNotNull(overview);
        assertTrue((int) overview.get("totalIngestionQps") > 0);
        assertTrue((int) overview.get("realtimeActiveDevices") > 0);
    }
}