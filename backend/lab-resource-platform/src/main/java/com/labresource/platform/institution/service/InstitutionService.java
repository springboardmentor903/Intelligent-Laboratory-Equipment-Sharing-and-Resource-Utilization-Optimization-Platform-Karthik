package com.labresource.platform.institution.service;

import com.labresource.platform.institution.dto.InstitutionRegistrationRequest;
import com.labresource.platform.institution.entity.Institution;
import com.labresource.platform.institution.repository.InstitutionRepository;
import com.labresource.platform.user.entity.Role;
import com.labresource.platform.user.entity.User;
import com.labresource.platform.user.entity.UserRole;
import com.labresource.platform.user.repository.RoleRepository;
import com.labresource.platform.user.repository.UserRepository;
import com.labresource.platform.user.repository.UserRoleRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class InstitutionService {

    private final InstitutionRepository institutionRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public InstitutionService(
            InstitutionRepository institutionRepository,
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository) {
        this.institutionRepository = institutionRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    public List<Institution> getAllInstitutions() {
        return institutionRepository.findAll();
    }

    public List<Institution> getPendingInstitutions() {
        return institutionRepository.findByStatus("PENDING");
    }

    public Optional<Institution> getInstitutionById(Long id) {
        return institutionRepository.findById(id);
    }

    @Transactional
    public Institution registerInstitution(InstitutionRegistrationRequest request) {
        String trimmedName = request.getName() != null ? request.getName().trim() : "";
        if (trimmedName.isEmpty()) {
            throw new IllegalArgumentException("Institution name is required.");
        }
        if (institutionRepository.existsByNameIgnoreCase(trimmedName)) {
            throw new IllegalArgumentException("An institution with this name already exists.");
        }

        String adminEmail = request.getAdminEmail() != null ? request.getAdminEmail().trim().toLowerCase() : "";
        if (adminEmail.isEmpty()) {
            throw new IllegalArgumentException("Admin email is required.");
        }
        if (userRepository.existsByEmail(adminEmail)) {
            throw new IllegalArgumentException("An account with this administrator email already exists.");
        }

        if (request.getAdminPassword() == null || request.getAdminPassword().length() < 8) {
            throw new IllegalArgumentException("Admin password must be at least 8 characters.");
        }

        Institution institution = new Institution();
        institution.setName(trimmedName);
        institution.setAddress(request.getAddress() != null ? request.getAddress().trim() : null);
        institution.setLocation(request.getLocation() != null ? request.getLocation().trim() : null);
        institution.setContactEmail(request.getContactEmail() != null ? request.getContactEmail().trim().toLowerCase() : null);
        institution.setPhoneNumber(request.getPhoneNumber() != null ? request.getPhoneNumber().trim() : null);
        institution.setStatus("PENDING");
        institution.setCreatedAt(LocalDateTime.now());

        Institution savedInstitution = institutionRepository.save(institution);

        // Create the initial prospective Institution Admin user in PENDING state
        User adminUser = new User();
        adminUser.setInstitutionId(savedInstitution.getId());
        adminUser.setEmail(adminEmail);
        adminUser.setFullName(request.getAdminFullName() != null ? request.getAdminFullName().trim() : null);
        adminUser.setPhoneNumber(request.getAdminPhoneNumber() != null ? request.getAdminPhoneNumber().trim() : null);
        adminUser.setPasswordHash(passwordEncoder.encode(request.getAdminPassword()));
        adminUser.setMembershipApproved(false);
        adminUser.setMembershipStatus("PENDING");
        adminUser.setRequestedRole("INSTITUTION_ADMIN");
        adminUser.setCreatedAt(LocalDateTime.now());

        userRepository.save(adminUser);

        // Note: No active role is saved in user_roles until System Admin approves the institution
        return savedInstitution;
    }

    @Transactional
    public Institution approveInstitution(Long institutionId, String reviewerEmail) {
        User reviewer = requireSystemAdmin(reviewerEmail);

        Institution institution = institutionRepository.findById(institutionId)
                .orElseThrow(() -> new IllegalArgumentException("Institution not found."));

        institution.setStatus("APPROVED");
        institution.setReviewedBy(reviewer.getId());
        institution.setReviewedAt(LocalDateTime.now());
        Institution saved = institutionRepository.save(institution);

        // Activate the primary institution administrator(s)
        Role instAdminRole = roleRepository.findByName("INSTITUTION_ADMIN")
                .orElseThrow(() -> new IllegalStateException("Role INSTITUTION_ADMIN not found."));

        List<User> pendingAdmins = userRepository.findByInstitutionIdAndRequestedRoleAndMembershipStatus(
                institutionId, "INSTITUTION_ADMIN", "PENDING");

        for (User admin : pendingAdmins) {
            Role sysAdminRole = roleRepository.findByName("SYSTEM_ADMIN").orElse(null);
            if (sysAdminRole != null && userRoleRepository.existsByUserIdAndRoleId(admin.getId(), sysAdminRole.getId())) {
                throw new IllegalStateException("Cannot grant INSTITUTION_ADMIN to user holding SYSTEM_ADMIN.");
            }
            if (admin.getInstitutionId() == null) {
                throw new IllegalStateException("Institution Administrator must be associated with an institution.");
            }

            admin.setMembershipApproved(true);
            admin.setMembershipStatus("APPROVED");
            admin.setReviewedBy(reviewer.getId());
            admin.setReviewedAt(LocalDateTime.now());
            User savedAdmin = userRepository.save(admin);

            if (!userRoleRepository.existsByUserIdAndRoleId(savedAdmin.getId(), instAdminRole.getId())) {
                userRoleRepository.save(new UserRole(savedAdmin.getId(), instAdminRole.getId()));
            }
        }

        return saved;
    }

    @Transactional
    public Institution rejectInstitution(Long institutionId, String reviewerEmail, String reason) {
        User reviewer = requireSystemAdmin(reviewerEmail);

        Institution institution = institutionRepository.findById(institutionId)
                .orElseThrow(() -> new IllegalArgumentException("Institution not found."));

        institution.setStatus("REJECTED");
        institution.setRejectionReason(reason != null ? reason.trim() : "Rejected by platform administrator");
        institution.setReviewedBy(reviewer.getId());
        institution.setReviewedAt(LocalDateTime.now());
        Institution saved = institutionRepository.save(institution);

        List<User> pendingAdmins = userRepository.findByInstitutionIdAndRequestedRoleAndMembershipStatus(
                institutionId, "INSTITUTION_ADMIN", "PENDING");

        for (User admin : pendingAdmins) {
            admin.setMembershipApproved(false);
            admin.setMembershipStatus("REJECTED");
            admin.setRejectionReason(institution.getRejectionReason());
            admin.setReviewedBy(reviewer.getId());
            admin.setReviewedAt(LocalDateTime.now());
            userRepository.save(admin);
        }

        return saved;
    }

    @Transactional
    public Institution updateStatus(Long institutionId, String status) {
        Institution institution = institutionRepository.findById(institutionId)
                .orElseThrow(() -> new IllegalArgumentException("Institution not found."));
        String normalizedStatus = status != null ? status.trim().toUpperCase() : "APPROVED";
        institution.setStatus(normalizedStatus);
        return institutionRepository.save(institution);
    }

    private User requireSystemAdmin(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Authenticated administrator is required.");
        }
        User user = userRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("Administrator not found."));
        boolean isSysAdmin = userRoleRepository.findByUserId(user.getId()).stream()
                .map(UserRole::getRoleId)
                .map(roleRepository::findById)
                .flatMap(Optional::stream)
                .map(Role::getName)
                .anyMatch("SYSTEM_ADMIN"::equals);
        if (!isSysAdmin || Boolean.FALSE.equals(user.getMembershipApproved())) {
            throw new IllegalArgumentException("An approved System Administrator is required.");
        }
        return user;
    }
}
