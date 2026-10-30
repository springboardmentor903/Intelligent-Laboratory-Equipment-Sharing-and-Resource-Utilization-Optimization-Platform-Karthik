package com.labresource.platform.user;

import com.labresource.platform.institution.entity.Institution;
import com.labresource.platform.institution.repository.InstitutionRepository;
import com.labresource.platform.user.entity.Role;
import com.labresource.platform.user.entity.User;
import com.labresource.platform.user.entity.UserRole;
import com.labresource.platform.user.repository.RoleRepository;
import com.labresource.platform.user.repository.UserRepository;
import com.labresource.platform.user.repository.UserRoleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Order(10)
public class SystemAdminBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SystemAdminBootstrap.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final InstitutionRepository institutionRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public SystemAdminBootstrap(
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            InstitutionRepository institutionRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.institutionRepository = institutionRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    @Override
    public void run(String... args) {
        Role systemAdminRole = roleRepository.findByName("SYSTEM_ADMIN").orElse(null);
        if (systemAdminRole == null) {
            log.warn("SystemAdminBootstrap: SYSTEM_ADMIN role not found yet.");
            return;
        }

        boolean hasSystemAdmin = userRoleRepository.existsByRoleId(systemAdminRole.getId());
        if (hasSystemAdmin) {
            log.info("SystemAdminBootstrap: Verified existing active platform System Administrator.");
            return;
        }

        log.warn("SystemAdminBootstrap: No System Administrator found! Activating bootstrap recovery account.");

        User bootstrapUser = userRepository.findByEmail("bunty@1234.com")
                .orElseGet(() -> {
                    User u = new User();
                    u.setEmail("bunty@1234.com");
                    u.setFullName("Platform System Administrator");
                    u.setInstitutionId(null);
                    u.setDepartmentId(null);
                    u.setPasswordHash(passwordEncoder.encode("Bunty@12345"));
                    u.setMembershipApproved(true);
                    u.setMembershipStatus("APPROVED");
                    u.setRequestedRole("SYSTEM_ADMIN");
                    u.setCreatedAt(LocalDateTime.now());
                    return userRepository.save(u);
                });

        bootstrapUser.setMembershipApproved(true);
        bootstrapUser.setMembershipStatus("APPROVED");
        userRepository.save(bootstrapUser);

        userRoleRepository.save(new UserRole(bootstrapUser.getId(), systemAdminRole.getId()));
        log.info("SystemAdminBootstrap: System Administrator recovery account provisioned: bunty@1234.com");
    }
}
