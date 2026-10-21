
package com.labresource.platform.user.service;

import com.labresource.platform.institution.repository.InstitutionRepository;
import com.labresource.platform.user.dto.UserRegistrationRequest;
import com.labresource.platform.user.entity.Role;
import com.labresource.platform.user.entity.User;
import com.labresource.platform.user.entity.UserRole;
import com.labresource.platform.user.repository.RoleRepository;
import com.labresource.platform.user.repository.UserRepository;
import com.labresource.platform.user.repository.UserRoleRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final InstitutionRepository institutionRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public UserService(
            UserRepository userRepository,
            InstitutionRepository institutionRepository,
            UserRoleRepository userRoleRepository,
            RoleRepository roleRepository) {

        this.userRepository = userRepository;
        this.institutionRepository = institutionRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email.trim().toLowerCase());
    }

    public boolean emailExists(String email) {
        return userRepository.existsByEmail(email.trim().toLowerCase());
    }

    @Transactional
    public User register(UserRegistrationRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException(
                    "An account with this email already exists.");
        }

        if (!institutionRepository.existsById(request.getInstitutionId())) {
            throw new IllegalArgumentException(
                    "The specified institution does not exist.");
        }

        Role defaultRole = roleRepository.findByName("RESEARCHER_STUDENT")
                .orElseThrow(() -> new IllegalStateException(
                        "Default role RESEARCHER_STUDENT was not found."));

        User user = new User();
        user.setInstitutionId(request.getInstitutionId());
        user.setEmail(email);
        user.setPasswordHash(
                passwordEncoder.encode(request.getPassword()));

        User savedUser = userRepository.save(user);

        UserRole userRole = new UserRole(
                savedUser.getId(),
                defaultRole.getId());

        userRoleRepository.save(userRole);

        return savedUser;
    }

    public User authenticate(String email, String password) {
        String normalizedEmail = email.trim().toLowerCase();

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Invalid email or password."));

        if (!passwordEncoder.matches(
                password, user.getPasswordHash())) {
            throw new IllegalArgumentException(
                    "Invalid email or password.");
        }

        return user;
    }
}
