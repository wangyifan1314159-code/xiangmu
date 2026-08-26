package com.iot.controller;

import com.iot.config.SecurityUtils;
import com.iot.dto.ApiResponse;
import com.iot.service.TcpListenerManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TcpControllerTest {

    @Mock
    private SecurityUtils securityUtils;

    @Mock
    private TcpListenerManager listenerManager;

    @Test
    void adminCanManageListeners() {
        TcpController controller = controller();
        Map<String, Object> listener = Map.of("port", 1885, "status", "LISTENING");
        when(securityUtils.hasRole("ADMIN")).thenReturn(true);
        when(listenerManager.listListeners()).thenReturn(List.of(listener));
        when(listenerManager.start(1885)).thenReturn(listener);
        when(listenerManager.stop(1885)).thenReturn(true);

        ApiResponse<List<Map<String, Object>>> listed = controller.listeners();
        ApiResponse<Map<String, Object>> started = controller.startListener(Map.of("port", 1885));
        ApiResponse<Map<String, Object>> stopped = controller.stopListener(1885);

        assertEquals(List.of(listener), listed.getData());
        assertEquals(listener, started.getData());
        assertEquals(1885, stopped.getData().get("port"));
        verify(listenerManager).stop(1885);
    }

    @Test
    void nonAdminCannotManageListeners() {
        TcpController controller = controller();
        when(securityUtils.hasRole("ADMIN")).thenReturn(false);

        assertThrows(RuntimeException.class, () -> controller.startListener(Map.of("port", 1885)));
        assertThrows(RuntimeException.class, () -> controller.stopListener(1885));

        verifyNoInteractions(listenerManager);
    }

    @Test
    void disabledTcpManagersHaveEmptyLists() {
        TcpController controller = new TcpController(securityUtils, null);

        assertEquals(List.of(), controller.listeners().getData());
    }

    private TcpController controller() {
        return new TcpController(securityUtils, listenerManager);
    }
}
