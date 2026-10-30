package com.labresource.platform.user.service;

import com.labresource.platform.institution.entity.Department;
import com.labresource.platform.institution.entity.Institution;
import com.labresource.platform.institution.repository.DepartmentRepository;
import com.labresource.platform.institution.repository.InstitutionRepository;
import com.labresource.platform.user.dto.PendingMembershipResponse;
import com.labresource.platform.user.dto.SystemAdminApplicationRequest;
import com.labresource.platform.user.dto.UserRegistrationRequest;
import com.labresource.platform.user.entity.Role;
import com.labresource.platform.user.entity.User;
import com.labresource.platform.user.entity.UserRole;
import com.labresource.platform.user.repository.RoleRepository;
import com.labresource.platform.user.repository.UserRepository;
import com.labresource.platform.user.repository.UserRoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Objects;

@Service
public class UserService {

    private static final List<String> ALLOWED_REQUESTED_ROLES = List.of(
            "RESEARCHER_STUDENT",
            "LAB_TECHNICIAN",
            "LAB_MANAGER",
            "DEPARTMENT_HEAD",
            "INSTITUTION_ADMIN"
    );

    private final UserRepository userRepository;
    private final InstitutionRepository institutionRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final DepartmentRepository departmentRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public UserService(
            UserRepository userRepository,
            InstitutionRepository institutionRepository,
            UserRoleRepository userRoleRepository,
            RoleRepository roleRepository) {
        this(userRepository, institutionRepository, userRoleRepository, roleRepository, null);
    }

    @Autowired
    public UserService(
            UserRepository userRepository,
            InstitutionRepository institutionRepository,
            UserRoleRepository userRoleRepository,
            RoleRepository roleRepository,
            @Autowired(required = false) DepartmentRepository departmentRepository) {

        this.userRepository = userRepository;
        this.institutionRepository = institutionRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
        this.departmentRepository = departmentRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email.trim().toLowerCase());
    }

    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
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

        if (request.getInstitutionId() == null) {
            throw new IllegalArgumentException("Institution ID is required for user registration.");
        }

        if (!institutionRepository.existsById(request.getInstitutionId())) {
            throw new IllegalArgumentException(
                    "The specified institution does not exist.");
        }

        if (request.getDepartmentId() != null && departmentRepository != null) {
            Department dept = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new IllegalArgumentException("The specified department does not exist."));
            if (!request.getInstitutionId().equals(dept.getInstitutionId())) {
                throw new IllegalArgumentException("Department does not belong to the specified institution.");
            }
        }

        String requested = request.getRequestedRole() != null ? request.getRequestedRole().trim().toUpperCase() : "RESEARCHER_STUDENT";
        if (requested.isEmpty()) {
            requested = "RESEARCHER_STUDENT";
        }

        if ("SYSTEM_ADMIN".equals(requested)) {
            throw new IllegalArgumentException("System Admin role cannot be self-assigned during standard registration. Use the System Admin application flow.");
        }

        if (!ALLOWED_REQUESTED_ROLES.contains(requested)) {
            throw new IllegalArgumentException("Requested role must be one of: " + String.join(", ", ALLOWED_REQUESTED_ROLES));
        }

        Role defaultRole = roleRepository.findByName("RESEARCHER_STUDENT")
                .orElseThrow(() -> new IllegalStateException(
                        "Default role RESEARCHER_STUDENT was not found."));

        User user = new User();
        user.setInstitutionId(request.getInstitutionId());
        user.setDepartmentId(request.getDepartmentId());
        user.setEmail(email);
        user.setFullName(request.getFullName() != null ? request.getFullName().trim() : null);
        user.setPhoneNumber(request.getPhoneNumber() != null ? request.getPhoneNumber().trim() : null);
        user.setRequestedRole(requested);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setMembershipApproved(false);
        user.setMembershipStatus("PENDING");
        user.setCreatedAt(LocalDateTime.now());

        User savedUser = userRepository.save(user);

        UserRole userRole = new UserRole(savedUser.getId(), defaultRole.getId());
        userRoleRepository.save(userRole);

        return savedUser;
    }

    @Transactional
    public User applySystemAdmin(SystemAdminApplicationRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("An account with this email already exists.");
        }

        if (request.getPassword() == null || request.getPassword().length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters.");
        }

        User user = new User();
        user.setInstitutionId(null);
        user.setDepartmentId(null);
        user.setEmail(email);
        user.setFullName(request.getFullName().trim());
        user.setPhoneNumber(request.getPhoneNumber() != null ? request.getPhoneNumber().trim() : null);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRequestedRole("SYSTEM_ADMIN");
        user.setMembershipApproved(false);
        user.setMembershipStatus("PENDING");
        user.setCreatedAt(LocalDateTime.now());

        return userRepository.save(user);
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

    @Transactional(readOnly = true)
    public List<String> getRoleNames(Long userId) {
        return userRoleRepository.findByUserId(userId).stream()
                .map(UserRole::getRoleId)
                .map(roleRepository::findById)
                .flatMap(Optional::stream)
                .map(Role::getName)
                .distinct()
                .sorted()
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PendingMembershipResponse> getPendingSystemAdmins() {
        return userRepository.findByRequestedRoleAndMembershipStatus("SYSTEM_ADMIN", "PENDING").stream()
                .map(u -> new PendingMembershipResponse(
                        u.getId(),
                        u.getEmail(),
                        u.getFullName(),
                        u.getPhoneNumber(),
                        u.getInstitutionId(),
                        "Platform",
                        null,
                        null,
                        "SYSTEM_ADMIN",
                        u.getMembershipStatus(),
                        u.getCreatedAt(),
                        u.getRejectionReason(),
                        u.getReviewedBy(),
                        u.getReviewedAt()
                ))
                .toList();
    }

    @Transactional
    public User approveSystemAdmin(Long userId, String reviewerEmail) {
        User reviewer = requireApprovedSystemAdmin(reviewerEmail);
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found."));

        if (Objects.equals(reviewer.getId(), target.getId())) {
            throw new IllegalArgumentException("System Administrators cannot approve their own application.");
        }
        if (!"SYSTEM_ADMIN".equals(target.getRequestedRole())) {
            throw new IllegalArgumentException("User did not apply for the System Administrator role.");
        }
        if (Boolean.TRUE.equals(target.getMembershipApproved())) {
            throw new IllegalArgumentException("Application is already approved.");
        }

        List<String> targetRoles = getRoleNames(target.getId());
        if (targetRoles.contains("INSTITUTION_ADMIN")) {
            throw new IllegalStateException("Cannot grant SYSTEM_ADMIN to a user holding the INSTITUTION_ADMIN role.");
        }

        target.setInstitutionId(null);
        target.setDepartmentId(null);
        target.setMembershipApproved(true);
        target.setMembershipStatus("APPROVED");
        target.setReviewedBy(reviewer.getId());
        target.setReviewedAt(LocalDateTime.now());
        User savedUser = userRepository.save(target);

        Role sysAdminRole = roleRepository.findByName("SYSTEM_ADMIN")
                .orElseThrow(() -> new IllegalStateException("Role SYSTEM_ADMIN not found."));

        userRoleRepository.findByUserId(savedUser.getId()).stream()
                .filter(ur -> !ur.getRoleId().equals(sysAdminRole.getId()))
                .forEach(userRoleRepository::delete);

        if (!userRoleRepository.existsByUserIdAndRoleId(savedUser.getId(), sysAdminRole.getId())) {
            userRoleRepository.save(new UserRole(savedUser.getId(), sysAdminRole.getId()));
        }

        return savedUser;
    }

    @Transactional
    public User rejectSystemAdmin(Long userId, String reviewerEmail, String reason) {
        User reviewer = requireApprovedSystemAdmin(reviewerEmail);
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found."));

        if (Objects.equals(reviewer.getId(), target.getId())) {
            throw new IllegalArgumentException("System Administrators cannot reject their own application.");
        }

        target.setMembershipApproved(false);
        target.setMembershipStatus("REJECTED");
        target.setRejectionReason(reason != null ? reason.trim() : "Application rejected by platform administrator");
        target.setReviewedBy(reviewer.getId());
        target.setReviewedAt(LocalDateTime.now());
        return userRepository.save(target);
    }

    @Transactional(readOnly = true)
    public List<PendingMembershipResponse> getPendingMemberships(String administratorEmail) {
        User administrator = requireApprovedAdministrator(administratorEmail);
        boolean isSysAdmin = getRoleNames(administrator.getId()).contains("SYSTEM_ADMIN");

        List<User> pendingUsers = isSysAdmin
                ? userRepository.findByMembershipStatus("PENDING").stream()
                .filter(u -> !"SYSTEM_ADMIN".equals(u.getRequestedRole()))
                .toList()
                : userRepository.findByInstitutionIdAndMembershipApprovedFalse(administrator.getInstitutionId()).stream()
                .filter(u -> !"SYSTEM_ADMIN".equals(u.getRequestedRole()) && !"REJECTED".equalsIgnoreCase(u.getMembershipStatus()))
                .toList();

        return pendingUsers.stream().map(u -> {
            String instName = (u.getInstitutionId() != null)
                    ? institutionRepository.findById(u.getInstitutionId()).map(Institution::getName).orElse(null)
                    : null;
            String deptName = (u.getDepartmentId() != null && departmentRepository != null)
                    ? departmentRepository.findById(u.getDepartmentId()).map(Department::getName).orElse(null)
                    : null;
            return new PendingMembershipResponse(
                    u.getId(),
                    u.getEmail(),
                    u.getFullName(),
                    u.getPhoneNumber(),
                    u.getInstitutionId(),
                    instName,
                    u.getDepartmentId(),
                    deptName,
                    u.getRequestedRole() != null ? u.getRequestedRole() : "RESEARCHER_STUDENT",
                    u.getMembershipStatus() != null ? u.getMembershipStatus() : "PENDING",
                    u.getCreatedAt(),
                    u.getRejectionReason(),
                    u.getReviewedBy(),
                    u.getReviewedAt()
            );
        }).toList();
    }

    @Transactional
    public User approveMembership(Long userId, String administratorEmail) {
        User administrator = requireApprovedAdministrator(administratorEmail);
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found."));

        if (Objects.equals(administrator.getId(), target.getId())) {
            throw new IllegalArgumentException("Administrators cannot approve their own membership.");
        }

        boolean isSysAdmin = getRoleNames(administrator.getId()).contains("SYSTEM_ADMIN");
        boolean isTargetInstAdmin = "INSTITUTION_ADMIN".equals(target.getRequestedRole());

        List<String> targetRoles = getRoleNames(target.getId());
        if (targetRoles.contains("SYSTEM_ADMIN")) {
            throw new IllegalStateException("Users holding the SYSTEM_ADMIN role cannot be assigned institution memberships or roles.");
        }
        if ("SYSTEM_ADMIN".equals(target.getRequestedRole())) {
            throw new IllegalArgumentException("System Administrator applications must be approved via the System Admin approval workflow.");
        }

        if (isTargetInstAdmin) {
            if (target.getInstitutionId() == null) {
                throw new IllegalStateException("Institution Administrator must be associated with an institution.");
            }
            // Check if target's institution has an active approved Institution Admin
            boolean hasActiveInstAdmin = userRepository.findByInstitutionIdAndMembershipStatus(
                    target.getInstitutionId(), "APPROVED").stream()
                    .anyMatch(u -> getRoleNames(u.getId()).contains("INSTITUTION_ADMIN"));

            if (!hasActiveInstAdmin) {
                // If no active Institution Admin exists, only System Admin can approve the initial admin
                if (!isSysAdmin) {
                    throw new IllegalArgumentException("Only a System Administrator can approve the initial Institution Administrator for this institution.");
                }
            } else {
                // An active Institution Admin of that institution, or a System Admin, can approve
                if (!isSysAdmin && !Objects.equals(administrator.getInstitutionId(), target.getInstitutionId())) {
                    throw new IllegalArgumentException("User belongs to a different institution.");
                }
            }
        } else {
            // Normal membership approval: must belong to same institution unless System Admin
            if (!isSysAdmin && !Objects.equals(administrator.getInstitutionId(), target.getInstitutionId())) {
                throw new IllegalArgumentException("User belongs to a different institution.");
            }
        }

        if (!Boolean.FALSE.equals(target.getMembershipApproved())) {
            throw new IllegalArgumentException("User does not have a pending membership.");
        }

        target.setMembershipApproved(true);
        target.setMembershipStatus("APPROVED");
        target.setReviewedBy(administrator.getId());
        target.setReviewedAt(LocalDateTime.now());
        User savedUser = userRepository.save(target);

        // Assign the user's requested role upon approval
        String roleToAssign = target.getRequestedRole() != null && !target.getRequestedRole().isBlank()
                ? target.getRequestedRole()
                : "RESEARCHER_STUDENT";

        if ("SYSTEM_ADMIN".equals(roleToAssign)) {
            throw new IllegalArgumentException("System Administrator role cannot be assigned through standard membership approval.");
        }

        roleRepository.findByName(roleToAssign).ifPresent(targetRole -> {
            if (!userRoleRepository.existsByUserIdAndRoleId(savedUser.getId(), targetRole.getId())) {
                userRoleRepository.save(new UserRole(savedUser.getId(), targetRole.getId()));
            }
        });

        return savedUser;
    }

    @Transactional
    public User rejectMembership(Long userId, String administratorEmail, String reason) {
        User administrator = requireApprovedAdministrator(administratorEmail);
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found."));

        if (Objects.equals(administrator.getId(), target.getId())) {
            throw new IllegalArgumentException("Administrators cannot reject their own membership.");
        }
        boolean isSysAdmin = getRoleNames(administrator.getId()).contains("SYSTEM_ADMIN");
        if (!isSysAdmin && !Objects.equals(administrator.getInstitutionId(), target.getInstitutionId())) {
            throw new IllegalArgumentException("User belongs to a different institution.");
        }

        target.setMembershipApproved(false);
        target.setMembershipStatus("REJECTED");
        target.setRejectionReason(reason != null ? reason.trim() : "Rejected by institution administrator");
        target.setReviewedBy(administrator.getId());
        target.setReviewedAt(LocalDateTime.now());
        return userRepository.save(target);
    }

    @Transactional
    public User updateProfile(Long userId, Long departmentId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found."));

        if (getRoleNames(user.getId()).contains("SYSTEM_ADMIN")) {
            if (departmentId != null) {
                throw new IllegalArgumentException("System Administrator cannot be assigned to any department.");
            }
            user.setDepartmentId(null);
            user.setInstitutionId(null);
            return userRepository.save(user);
        }

        if (user.getInstitutionId() == null && departmentId != null) {
            throw new IllegalArgumentException("User without an assigned institution cannot be assigned a department.");
        }

        if (departmentId != null && departmentRepository != null) {
            Department dept = departmentRepository.findById(departmentId)
                    .orElseThrow(() -> new IllegalArgumentException("The specified department does not exist."));
            if (user.getInstitutionId() == null || !user.getInstitutionId().equals(dept.getInstitutionId())) {
                throw new IllegalArgumentException("Department does not belong to the user's institution.");
            }
        }
        user.setDepartmentId(departmentId);
        return userRepository.save(user);
    }

    private User requireApprovedAdministrator(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Authenticated administrator is required.");
        }
        User administrator = userRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("Administrator not found."));
        var roles = getRoleNames(administrator.getId());
        if (Boolean.FALSE.equals(administrator.getMembershipApproved())
                || (!roles.contains("INSTITUTION_ADMIN") && !roles.contains("SYSTEM_ADMIN"))) {
            throw new IllegalArgumentException("An approved institution or system administrator is required.");
        }
        return administrator;
    }

    private User requireApprovedSystemAdmin(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Authenticated administrator is required.");
        }
        User administrator = userRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("Administrator not found."));
        var roles = getRoleNames(administrator.getId());
        if (Boolean.FALSE.equals(administrator.getMembershipApproved()) || !roles.contains("SYSTEM_ADMIN")) {
            throw new IllegalArgumentException("An approved System Administrator is required.");
        }
        return administrator;
    }
}
