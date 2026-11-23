package com.labresource.platform.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Arrays;

/**
 * Seeder for populating realistic multi-institution presentation demo data.
 * Can be triggered via:
 * 1. Property: app.demo.seed=true (e.g. -Dapp.demo.seed=true)
 * 2. Environment variable: DEMO_RESET_SEED=true
 * 3. Command line argument: --seed-demo-data
 * 4. REST endpoint: POST /api/admin/demo-seed (SYSTEM_ADMIN only)
 */
@Component
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final DataSource dataSource;

    @Value("${app.demo.seed:false}")
    private boolean seedProperty;

    @Value("${DEMO_RESET_SEED:false}")
    private boolean seedEnv;

    public DemoDataSeeder(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) {
        boolean argRequested = args != null && Arrays.asList(args).contains("--seed-demo-data");
        if (seedProperty || seedEnv || argRequested) {
            log.info("DemoDataSeeder triggered (seedProperty={}, seedEnv={}, argRequested={}). Seeding demo dataset...",
                    seedProperty, seedEnv, argRequested);
            resetAndSeedDemoData();
        } else {
            log.info("DemoDataSeeder skipped (app.demo.seed=false).");
        }
    }

    public synchronized void resetAndSeedDemoData() {
        try {
            log.info("Executing demo_seed.sql against active datasource...");
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource("demo_seed.sql"));
            populator.setContinueOnError(false);
            populator.execute(dataSource);
            log.info("Demo dataset populated successfully: 3 institutions, 7 departments, 11 users, 9 equipment, work orders, calibrations & analytics.");
        } catch (Exception ex) {
            log.error("Failed to execute demo_seed.sql: {}", ex.getMessage(), ex);
            throw new RuntimeException("Demo seed execution failed: " + ex.getMessage(), ex);
        }
    }
}
