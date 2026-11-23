package com.labresource.platform.demo;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminDemoController {

    private final DemoDataSeeder demoDataSeeder;

    public AdminDemoController(DemoDataSeeder demoDataSeeder) {
        this.demoDataSeeder = demoDataSeeder;
    }

    @PostMapping("/demo-seed")
    @PreAuthorize("@resourceAccess.isSystemAdmin(authentication)")
    public ResponseEntity<Map<String, Object>> resetAndSeedDemo() {
        demoDataSeeder.resetAndSeedDemoData();
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Demo database reset and seeded successfully.",
                "institutions", 3,
                "users", 11,
                "equipment", 9
        ));
    }
}
