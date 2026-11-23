package com.labresource.platform.demo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminDemoControllerTests {

    @Mock
    private DemoDataSeeder demoDataSeeder;

    private AdminDemoController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminDemoController(demoDataSeeder);
    }

    @Test
    void resetAndSeedDemoCallsSeederAndReturnsSuccessResponse() {
        doNothing().when(demoDataSeeder).resetAndSeedDemoData();

        ResponseEntity<Map<String, Object>> response = controller.resetAndSeedDemo();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo("SUCCESS");
        assertThat(response.getBody().get("institutions")).isEqualTo(3);
        assertThat(response.getBody().get("users")).isEqualTo(11);
        assertThat(response.getBody().get("equipment")).isEqualTo(9);

        verify(demoDataSeeder).resetAndSeedDemoData();
    }
}
