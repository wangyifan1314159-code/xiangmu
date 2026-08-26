package com.iot.dataservice;

import com.iot.dataservice.service.OlapAnalyticsService;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

public class OlapAnalyticsServiceTest {

    private final OlapAnalyticsService service = new OlapAnalyticsService();

    @Test
    public void testQueryMetricSummary() {
        Map<String, Object> result = service.queryMetricSummary("INDUSTRIAL_GATEWAY", "1h", "7d");
        assertNotNull(result);
        assertEquals("INDUSTRIAL_GATEWAY", result.get("productType"));
        assertTrue(result.containsKey("trend"));
        List<?> trend = (List<?>) result.get("trend");
        assertFalse(trend.isEmpty());
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