package com.labresource.platform;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:platform-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=false",
        "jwt.secret=VGhpcyBpcyBhIHRlc3Qgc2VjcmV0IGtleSBmb3IgSldU"
})
class LabResourcePlatformApplicationTests {

    @Test
    void contextLoads() {
    }

}
