
package com.labresource.platform.user;

import com.labresource.platform.user.entity.Role;
import com.labresource.platform.user.repository.RoleRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class RoleInitializer {

    @Bean
    CommandLineRunner initializeRoles(RoleRepository roleRepository) {
        return args -> {
            if (roleRepository.count() == 0) {
                roleRepository.saveAll(List.of(
                        new Role("RESEARCHER_STUDENT",
                                "Searches equipment and makes bookings"),
                        new Role("LAB_TECHNICIAN",
                                "Performs maintenance and calibration tasks"),
                        new Role("LAB_MANAGER",
                                "Approves bookings and monitors lab usage"),
                        new Role("DEPARTMENT_HEAD",
                                "Oversees all labs of a department"),
                        new Role("INSTITUTION_ADMIN",
                                "Manages the institution, its users and sharing"),
                        new Role("SYSTEM_ADMIN",
                                "Manages the whole platform")
                ));
            }
        };
    }
}
