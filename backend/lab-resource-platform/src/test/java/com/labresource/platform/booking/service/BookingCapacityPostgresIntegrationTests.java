package com.labresource.platform.booking.service;

import com.labresource.platform.booking.entity.Booking;
import com.labresource.platform.equipment.entity.Equipment;
import com.labresource.platform.equipment.repository.EquipmentRepository;
import com.labresource.platform.institution.entity.Institution;
import com.labresource.platform.institution.repository.InstitutionRepository;
import com.labresource.platform.user.entity.User;
import com.labresource.platform.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Runs only against a disposable PostgreSQL container and is skipped when Docker is unavailable. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "jwt.expiration-ms=60000"
})
class BookingCapacityPostgresIntegrationTests {
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private BookingService bookingService;
    @Autowired private InstitutionRepository institutions;
    @Autowired private EquipmentRepository equipmentRepository;
    @Autowired private UserRepository users;

    @Test
    void concurrentReservationsNeverExceedOneUnit() throws Exception {
        Fixture fixture = fixture(1, 2);
        assertEquals(1, submitConcurrently(fixture).stream().filter(Boolean::booleanValue).count());
    }

    @Test
    void concurrentReservationsRespectMultipleEquipmentUnits() throws Exception {
        Fixture fixture = fixture(2, 3);
        assertEquals(2, submitConcurrently(fixture).stream().filter(Boolean::booleanValue).count());
    }

    private List<Boolean> submitConcurrently(Fixture fixture) throws Exception {
        CountDownLatch ready = new CountDownLatch(fixture.users().size());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(fixture.users().size());
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (User user : fixture.users()) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        Booking booking = new Booking();
                        booking.setEquipmentId(fixture.equipmentId());
                        booking.setStartTime(fixture.start());
                        booking.setEndTime(fixture.start().plusHours(1));
                        booking.setPurpose("concurrency integration test");
                        bookingService.createBooking(booking, user.getEmail());
                        return true;
                    } catch (IllegalArgumentException capacityReached) {
                        return false;
                    }
                }));
            }
            ready.await();
            start.countDown();
            List<Boolean> completed = new ArrayList<>();
            for (Future<Boolean> result : results) completed.add(result.get());
            return completed;
        } finally {
            executor.shutdownNow();
        }
    }

    private Fixture fixture(int quantity, int numberOfUsers) {
        Institution institution = new Institution();
        institution.setName("Booking concurrency test " + System.nanoTime());
        institution = institutions.save(institution);

        Equipment equipment = new Equipment();
        equipment.setName("Concurrency test equipment");
        equipment.setInstitutionId(institution.getId());
        equipment.setQuantity(quantity);
        equipment.setStatus("AVAILABLE");
        equipment.setTags(java.util.Set.of());
        equipment = equipmentRepository.save(equipment);

        List<User> fixtureUsers = new ArrayList<>();
        for (int index = 0; index < numberOfUsers; index++) {
            User user = new User();
            user.setEmail("booking-test-" + System.nanoTime() + "-" + index + "@example.test");
            user.setPasswordHash("test-only-not-a-real-password-hash");
            user.setInstitutionId(institution.getId());
            user.setMembershipApproved(true);
            fixtureUsers.add(users.save(user));
        }
        return new Fixture(equipment.getId(), fixtureUsers, LocalDateTime.now().plusDays(1));
    }

    private record Fixture(Long equipmentId, List<User> users, LocalDateTime start) { }
}
