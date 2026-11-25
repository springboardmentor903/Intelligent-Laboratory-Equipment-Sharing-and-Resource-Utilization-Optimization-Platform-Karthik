package com.labresource.platform.sharing.service;

import com.labresource.platform.booking.entity.Booking;
import com.labresource.platform.booking.entity.BookingAuditLog;
import com.labresource.platform.booking.event.BookingReleasedEvent;
import com.labresource.platform.booking.repository.BookingAuditLogRepository;
import com.labresource.platform.booking.repository.BookingRepository;
import com.labresource.platform.equipment.entity.Equipment;
import com.labresource.platform.equipment.repository.EquipmentRepository;
import com.labresource.platform.institution.repository.InstitutionRepository;
import com.labresource.platform.sharing.entity.SharingAccessGrant;
import com.labresource.platform.sharing.entity.SharingAgreement;
import com.labresource.platform.sharing.entity.SharingListing;
import com.labresource.platform.sharing.entity.SharingRequest;
import com.labresource.platform.sharing.repository.SharingAccessGrantRepository;
import com.labresource.platform.sharing.repository.SharingAgreementRepository;
import com.labresource.platform.sharing.repository.SharingListingRepository;
import com.labresource.platform.sharing.repository.SharingRequestRepository;
import com.labresource.platform.user.entity.Role;
import com.labresource.platform.user.entity.User;
import com.labresource.platform.user.repository.RoleRepository;
import com.labresource.platform.user.repository.UserRepository;
import com.labresource.platform.user.repository.UserRoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Service
public class SharingWorkflowService {
    private final SharingListingRepository listings;
    private final SharingRequestRepository requests;
    private final SharingAgreementRepository agreements;
    private final SharingAccessGrantRepository grants;
    private final EquipmentRepository equipment;
    private final UserRepository users;
    private final InstitutionRepository institutions;
    private final UserRoleRepository userRoles;
    private final RoleRepository roles;
    private final BookingRepository bookings;
    private final ApplicationEventPublisher events;
    private final BookingAuditLogRepository auditLogs;

    @Autowired(required = false)
    private com.labresource.platform.notification.service.NotificationService notificationService;

    public void setNotificationService(com.labresource.platform.notification.service.NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Autowired
    public SharingWorkflowService(
            SharingListingRepository listings,
            SharingRequestRepository requests,
            SharingAgreementRepository agreements,
            SharingAccessGrantRepository grants,
            EquipmentRepository equipment,
            UserRepository users,
            InstitutionRepository institutions,
            UserRoleRepository userRoles,
            RoleRepository roles,
            BookingRepository bookings,
            ApplicationEventPublisher events,
            @Autowired(required = false) BookingAuditLogRepository auditLogs) {
        this.listings = listings;
        this.requests = requests;
        this.agreements = agreements;
        this.grants = grants;
        this.equipment = equipment;
        this.users = users;
        this.institutions = institutions;
        this.userRoles = userRoles;
        this.roles = roles;
        this.bookings = bookings;
        this.events = events;
        this.auditLogs = auditLogs;
    }

    public record ListingInput(
            BigDecimal feePerHour,
            BigDecimal externalSharePercentage,
            Integer minimumNoticeHours,
            Integer minimumBookingNoticeHours,
            Integer maximumBookingHours,
            Integer maximumBookingDuration,
            String terms,
            String sharingTerms,
            Boolean active) {
        public ListingInput(BigDecimal feePerHour, BigDecimal externalSharePercentage, Integer minimumNoticeHours, Integer maximumBookingHours, String terms, Boolean active) {
            this(feePerHour, externalSharePercentage, minimumNoticeHours, minimumNoticeHours, maximumBookingHours, maximumBookingHours, terms, terms, active);
        }

        public Integer effectiveMinimumNoticeHours() {
            return minimumBookingNoticeHours != null ? minimumBookingNoticeHours : minimumNoticeHours;
        }

        public Integer effectiveMaximumBookingHours() {
            return maximumBookingDuration != null ? maximumBookingDuration : maximumBookingHours;
        }

        public String effectiveTerms() {
            return sharingTerms != null ? sharingTerms : terms;
        }
    }

    public record RequestInput(
            Long equipmentId,
            String purpose,
            LocalDateTime startDate,
            LocalDateTime endDate) {}

    private User actor(String email) {
        return users.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new SecurityException("Authenticated user not found"));
    }

    private boolean role(User u, String name) {
        return userRoles.findByUserId(u.getId()).stream()
                .map(x -> roles.findById(x.getRoleId()))
                .flatMap(Optional::stream)
                .anyMatch(r -> name.equals(r.getName()));
    }

    private boolean sys(User u) {
        return role(u, "SYSTEM_ADMIN");
    }

    private Equipment item(Long id) {
        return equipment.findByIdForBooking(id)
                .orElseThrow(() -> new IllegalArgumentException("Equipment not found"));
    }

    public boolean hasActiveInstitutionAdmin(Long institutionId) {
        if (institutionId == null) return false;
        Role adminRole = roles.findByName("INSTITUTION_ADMIN").orElse(null);
        if (adminRole == null) return false;
        return userRoles.findByRoleId(adminRole.getId()).stream()
                .map(ur -> users.findById(ur.getUserId()))
                .flatMap(Optional::stream)
                .anyMatch(user -> Objects.equals(user.getInstitutionId(), institutionId)
                        && !"REJECTED".equalsIgnoreCase(user.getMembershipStatus())
                        && !"PENDING".equalsIgnoreCase(user.getMembershipStatus())
                        && (user.getMembershipApproved() == null || Boolean.TRUE.equals(user.getMembershipApproved())));
    }

    @Transactional
    public SharingListing saveListing(Long id, ListingInput in, String email) {
        User u = actor(email);
        Equipment e = item(id);
        if (!sys(u) && (!role(u, "INSTITUTION_ADMIN") || !Objects.equals(u.getInstitutionId(), e.getInstitutionId()))) {
            throw new SecurityException("Owner Institution Administrator required");
        }
        if (!Boolean.TRUE.equals(e.getShareable())) {
            throw new IllegalArgumentException("Mark equipment as shareable first");
        }
        Integer minNotice = in.effectiveMinimumNoticeHours();
        Integer maxHours = in.effectiveMaximumBookingHours();
        String terms = in.effectiveTerms();
        if (in.feePerHour() == null || in.feePerHour().signum() < 0
                || in.externalSharePercentage() == null || in.externalSharePercentage().signum() < 0
                || in.externalSharePercentage().compareTo(BigDecimal.valueOf(100)) > 0
                || minNotice == null || minNotice < 0
                || maxHours == null || maxHours < 1) {
            throw new IllegalArgumentException("Listing values are invalid");
        }

        SharingListing l = listings.findByEquipmentId(id).orElseGet(SharingListing::new);
        l.setEquipmentId(id);
        l.setFeePerHour(in.feePerHour());
        l.setExternalSharePercentage(in.externalSharePercentage());
        l.setMinimumNoticeHours(minNotice);
        l.setMaximumBookingHours(maxHours);
        l.setTerms(terms == null ? "" : terms);
        if (in.active() != null) l.setActive(in.active());
        l.setUpdatedAt(LocalDateTime.now());
        return listings.save(l);
    }

    @Transactional
    public SharingListing setListingActive(Long id, boolean active, String email) {
        User u = actor(email);
        Equipment e = item(id);
        if (!sys(u) && (!role(u, "INSTITUTION_ADMIN") || !Objects.equals(u.getInstitutionId(), e.getInstitutionId()))) {
            throw new SecurityException("Owner Institution Administrator required");
        }
        if (active && !Boolean.TRUE.equals(e.getShareable())) {
            throw new IllegalArgumentException("Equipment must be marked shareable before activating listing");
        }
        SharingListing l = listings.findByEquipmentId(id)
                .orElseThrow(() -> new IllegalArgumentException("Sharing listing not found for equipment " + id));
        l.setActive(active);
        l.setUpdatedAt(LocalDateTime.now());
        return listings.save(l);
    }

    @Transactional
    public SharingRequest submit(RequestInput in, String email) {
        User u = actor(email);
        if (u.getInstitutionId() == null || !(role(u, "RESEARCHER_STUDENT") || role(u, "DEPARTMENT_HEAD"))) {
            throw new SecurityException("Institutional researcher or department head required");
        }
        if (!hasActiveInstitutionAdmin(u.getInstitutionId())) {
            throw new IllegalStateException("Requester institution must have an active institution administrator");
        }
        if (in.purpose() == null || in.purpose().isBlank() || in.purpose().length() > 500
                || in.startDate() == null || in.endDate() == null || !in.endDate().isAfter(in.startDate())) {
            throw new IllegalArgumentException("Purpose up to 500 characters and valid dates are required");
        }
        Equipment e = item(in.equipmentId());
        if (Objects.equals(e.getInstitutionId(), u.getInstitutionId())) {
            throw new IllegalArgumentException("Equipment must belong to another institution");
        }
        if (listings.findByEquipmentId(e.getId()).filter(SharingListing::getActive).isEmpty()) {
            throw new IllegalArgumentException("No active listing");
        }
        if (requests.hasOpenOverlap(e.getId(), in.startDate(), in.endDate())) {
            throw new IllegalArgumentException("An overlapping open request already exists");
        }

        SharingRequest r = new SharingRequest();
        r.setEquipmentId(e.getId());
        r.setRequesterId(u.getId());
        r.setRequesterInstitutionId(u.getInstitutionId());
        r.setPurpose(in.purpose().trim());
        r.setStartDate(in.startDate());
        r.setEndDate(in.endDate());
        SharingRequest saved = requests.save(r);

        if (notificationService != null) {
            try {
                List<User> admins = users.findByInstitutionIdAndRequestedRoleAndMembershipStatus(
                        u.getInstitutionId(), "INSTITUTION_ADMIN", "APPROVED");
                for (User admin : admins) {
                    notificationService.createNotification(
                            admin.getId(),
                            com.labresource.platform.notification.model.NotificationType.SHARING_REQUEST_SUBMITTED,
                            "External Sharing Request Submitted",
                            "A new equipment sharing request #" + saved.getId() + " for " + e.getName() + " was submitted by " + (u.getFullName() != null ? u.getFullName() : u.getEmail()) + ".",
                            com.labresource.platform.notification.model.ReferenceType.SHARING_REQUEST,
                            saved.getId()
                    );
                }
            } catch (Exception ignored) {
            }
        }

        return saved;
    }

    @Transactional
    public SharingRequest decide(Long id, String action, String remarks, String email) {
        return decide(id, action, remarks, null, null, email);
    }

    @Transactional
    public SharingRequest decide(Long id, String action, String remarks, LocalTime allowedStart, LocalTime allowedEnd, String email) {
        User u = actor(email);
        SharingRequest r = requests.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));
        Equipment e = item(r.getEquipmentId());
        String act = action.toUpperCase(Locale.ROOT);

        if ("PENDING".equals(r.getStatus())) {
            if (!sys(u) && (!role(u, "INSTITUTION_ADMIN") || !Objects.equals(u.getInstitutionId(), r.getRequesterInstitutionId()))) {
                throw new SecurityException("Requesting institution administrator required");
            }
            if (!List.of("FORWARD", "REJECT").contains(act)) {
                throw new IllegalArgumentException("Invalid action");
            }
            r.setRequesterReviewedBy(u.getId());
            r.setRequesterReviewedAt(LocalDateTime.now());
            r.setRequesterReviewRemarks(remarks);
            r.setStatus("FORWARD".equals(act) ? "FORWARDED" : "REJECTED");
            if ("REJECT".equals(act)) {
                requireReason(remarks);
                r.setReviewedBy(u.getId());
                r.setReviewedAt(LocalDateTime.now());
                r.setReviewRemarks(remarks);
            }
            SharingRequest saved = requests.save(r);
            if (notificationService != null) {
                try {
                    if ("FORWARDED".equals(saved.getStatus())) {
                        List<User> ownerAdmins = users.findByInstitutionIdAndRequestedRoleAndMembershipStatus(
                                e.getInstitutionId(), "INSTITUTION_ADMIN", "APPROVED");
                        for (User admin : ownerAdmins) {
                            notificationService.createNotification(
                                    admin.getId(),
                                    com.labresource.platform.notification.model.NotificationType.SHARING_REQUEST_FORWARDED,
                                    "Incoming Sharing Request Forwarded",
                                    "Sharing request #" + saved.getId() + " for equipment " + e.getName() + " has been forwarded for your review.",
                                    com.labresource.platform.notification.model.ReferenceType.SHARING_REQUEST,
                                    saved.getId()
                            );
                        }
                    } else if ("REJECTED".equals(saved.getStatus())) {
                        notificationService.createNotification(
                                saved.getRequesterId(),
                                com.labresource.platform.notification.model.NotificationType.SHARING_REQUEST_REJECTED,
                                "Sharing Request Rejected",
                                "Your sharing request #" + saved.getId() + " for " + e.getName() + " was rejected by your institution admin.",
                                com.labresource.platform.notification.model.ReferenceType.SHARING_REQUEST,
                                saved.getId()
                        );
                    }
                } catch (Exception ignored) {
                }
            }
            return saved;
        }

        if (!"FORWARDED".equals(r.getStatus()) || (!sys(u) && (!role(u, "INSTITUTION_ADMIN") || !Objects.equals(u.getInstitutionId(), e.getInstitutionId())))) {
            throw new SecurityException("Owner institution administrator required for forwarded request");
        }

        r.setReviewedBy(u.getId());
        r.setReviewedAt(LocalDateTime.now());
        r.setReviewRemarks(remarks);

        if ("REJECT".equals(act)) {
            requireReason(remarks);
            r.setStatus("REJECTED");
            SharingRequest saved = requests.save(r);
            if (notificationService != null) {
                try {
                    notificationService.createNotification(
                            saved.getRequesterId(),
                            com.labresource.platform.notification.model.NotificationType.SHARING_REQUEST_REJECTED,
                            "Sharing Request Rejected",
                            "Your sharing request #" + saved.getId() + " for " + e.getName() + " was rejected by the owner institution.",
                            com.labresource.platform.notification.model.ReferenceType.SHARING_REQUEST,
                            saved.getId()
                    );
                } catch (Exception ignored) {
                }
            }
            return saved;
        }
        if (!"APPROVE".equals(act)) {
            throw new IllegalArgumentException("Invalid action");
        }
        if ((allowedStart == null) != (allowedEnd == null) || (allowedStart != null && !allowedEnd.isAfter(allowedStart))) {
            throw new IllegalArgumentException("Allowed hours must include a valid start and end time");
        }

        SharingAgreement a = new SharingAgreement();
        a.setEquipmentId(e.getId());
        a.setOwnerInstitutionId(e.getInstitutionId());
        a.setPartnerInstitutionId(r.getRequesterInstitutionId());
        a.setRequestId(r.getId());
        a.setStartDate(r.getStartDate());
        a.setEndDate(r.getEndDate());
        a.setAllowedStartTime(allowedStart);
        a.setAllowedEndTime(allowedEnd);
        a.setFeePerHour(listings.findByEquipmentId(e.getId()).orElseThrow().getFeePerHour());
        a.setApprovedBy(u.getId());
        a = agreements.save(a);

        SharingAccessGrant g = new SharingAccessGrant();
        g.setAgreementId(a.getId());
        g.setUserId(r.getRequesterId());
        grants.save(g);

        r.setStatus("APPROVED");
        SharingRequest saved = requests.save(r);
        if (notificationService != null) {
            try {
                notificationService.createNotification(
                        saved.getRequesterId(),
                        com.labresource.platform.notification.model.NotificationType.SHARING_REQUEST_APPROVED,
                        "Sharing Request Approved",
                        "Your sharing request #" + saved.getId() + " for " + e.getName() + " has been approved! Agreement #" + a.getId() + " is now active.",
                        com.labresource.platform.notification.model.ReferenceType.SHARING_AGREEMENT,
                        a.getId()
                );
            } catch (Exception ignored) {
            }
        }
        return saved;
    }

    @Transactional
    public SharingRequest withdraw(Long id, String email) {
        User u = actor(email);
        SharingRequest r = requests.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));
        if (!Objects.equals(u.getId(), r.getRequesterId()) || !List.of("PENDING", "FORWARDED").contains(r.getStatus())) {
            throw new SecurityException("Cannot withdraw this request");
        }
        r.setStatus("WITHDRAWN");
        return requests.save(r);
    }

    public void enrichRequests(List<SharingRequest> list) {
        if (list == null || list.isEmpty()) return;
        List<Long> eqIds = list.stream().map(SharingRequest::getEquipmentId).filter(Objects::nonNull).distinct().toList();
        List<Long> instIds = list.stream().map(SharingRequest::getRequesterInstitutionId).filter(Objects::nonNull).distinct().toList();
        List<Long> userIds = list.stream().map(SharingRequest::getRequesterId).filter(Objects::nonNull).distinct().toList();

        Map<Long, Equipment> eqMap = new HashMap<>();
        if (!eqIds.isEmpty()) {
            equipment.findAllById(eqIds).forEach(e -> eqMap.put(e.getId(), e));
        }

        Map<Long, SharingListing> listMap = new HashMap<>();
        if (!eqIds.isEmpty()) {
            listings.findByEquipmentIdIn(eqIds).forEach(l -> listMap.put(l.getEquipmentId(), l));
        }

        Map<Long, String> instMap = new HashMap<>();
        if (!instIds.isEmpty()) {
            institutions.findAllById(instIds).forEach(i -> instMap.put(i.getId(), i.getName()));
        }

        Map<Long, User> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            users.findAllById(userIds).forEach(u -> userMap.put(u.getId(), u));
        }

        for (SharingRequest r : list) {
            Equipment e = eqMap.get(r.getEquipmentId());
            if (e != null) {
                r.setEquipmentName(e.getName());
                r.setAssetTag(e.getAssetTag());
            }
            SharingListing l = listMap.get(r.getEquipmentId());
            if (l != null) {
                r.setFeePerHour(l.getFeePerHour());
            }
            if (r.getRequesterInstitutionId() != null) {
                r.setPartnerInstitution(instMap.get(r.getRequesterInstitutionId()));
            }
            User u = userMap.get(r.getRequesterId());
            if (u != null) {
                r.setRequesterName(u.getFullName() != null && !u.getFullName().isBlank() ? u.getFullName() : u.getEmail());
                r.setRequesterEmail(u.getEmail());
            }
        }
    }

    public List<SharingRequest> requests(String view, String email) {
        User u = actor(email);
        List<SharingRequest> res = List.of();
        if ("mine".equals(view)) res = requests.findByRequesterIdOrderByCreatedAtDesc(u.getId());
        else if (role(u, "DEPARTMENT_HEAD")) {
            res = requests.findByRequesterInstitutionIdAndStatusOrderByCreatedAtDesc(u.getInstitutionId(), "PENDING");
        } else if (!sys(u) && !role(u, "INSTITUTION_ADMIN")) {
            throw new SecurityException("Administrator role required");
        } else if ("outgoing".equals(view)) {
            res = requests.findByRequesterInstitutionIdAndStatusOrderByCreatedAtDesc(u.getInstitutionId(), "PENDING");
        } else if ("incoming".equals(view)) {
            res = sys(u)
                    ? requests.findByStatusOrderByCreatedAtDesc("FORWARDED", PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent()
                    : requests.ownerQueue(u.getInstitutionId(), PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent();
        }
        enrichRequests(res);
        return res;
    }

    public Page<SharingRequest> requestsPage(String view, String email, int page, int size) {
        User u = actor(email);
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.min(50, Math.max(1, size)), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<SharingRequest> result = Page.empty(pageable);
        if ("mine".equals(view)) {
            result = requests.findByRequesterIdOrderByCreatedAtDesc(u.getId(), pageable);
        } else if (role(u, "DEPARTMENT_HEAD")) {
            result = requests.findByRequesterInstitutionIdAndStatusOrderByCreatedAtDesc(u.getInstitutionId(), "PENDING", pageable);
        } else if (!sys(u) && !role(u, "INSTITUTION_ADMIN")) {
            throw new SecurityException("Administrator role required");
        } else if ("outgoing".equals(view)) {
            result = requests.findByRequesterInstitutionIdAndStatusOrderByCreatedAtDesc(u.getInstitutionId(), "PENDING", pageable);
        } else if ("incoming".equals(view)) {
            result = sys(u)
                    ? requests.findByStatusOrderByCreatedAtDesc("FORWARDED", pageable)
                    : requests.ownerQueue(u.getInstitutionId(), pageable);
        }
        enrichRequests(result.getContent());
        return result;
    }

    public List<Map<String, Object>> agreements(String email) {
        User u = actor(email);
        List<SharingAgreement> all = sys(u) ? agreements.findAll() : u.getInstitutionId() == null ? List.of()
                : java.util.stream.Stream.concat(
                        agreements.findByOwnerInstitutionId(u.getInstitutionId()).stream(),
                        agreements.findByPartnerInstitutionId(u.getInstitutionId()).stream()
                ).distinct().toList();
        return all.stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("agreement", a);
            m.put("equipment", equipment.findById(a.getEquipmentId()).orElse(null));
            m.put("listing", listings.findByEquipmentId(a.getEquipmentId()).orElse(null));
            m.put("grants", grants.findByAgreementId(a.getId()));
            m.put("expired", a.getEndDate().isBefore(LocalDateTime.now()));
            return m;
        }).toList();
    }

    public Map<String, Object> getAgreement(Long id, String email) {
        User u = actor(email);
        SharingAgreement a = agreements.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Agreement not found"));
        boolean system = sys(u);
        boolean ownerAdmin = role(u, "INSTITUTION_ADMIN") && Objects.equals(u.getInstitutionId(), a.getOwnerInstitutionId());
        boolean partnerAdmin = role(u, "INSTITUTION_ADMIN") && Objects.equals(u.getInstitutionId(), a.getPartnerInstitutionId());
        boolean hasGrant = grants.findByAgreementIdAndUserId(id, u.getId()).stream().anyMatch(g -> "ACTIVE".equals(g.getStatus()));

        if (!system && !ownerAdmin && !partnerAdmin && !hasGrant) {
            throw new SecurityException("You do not have permission to view this agreement");
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("agreement", a);
        m.put("equipment", equipment.findById(a.getEquipmentId()).orElse(null));
        m.put("listing", listings.findByEquipmentId(a.getEquipmentId()).orElse(null));
        m.put("grants", grants.findByAgreementId(a.getId()));
        m.put("expired", a.getEndDate().isBefore(LocalDateTime.now()));
        return m;
    }

    public List<SharingAccessGrant> getAgreementGrants(Long agreementId, String email) {
        User u = actor(email);
        SharingAgreement a = agreements.findById(agreementId)
                .orElseThrow(() -> new IllegalArgumentException("Agreement not found"));
        boolean system = sys(u);
        boolean ownerAdmin = role(u, "INSTITUTION_ADMIN") && Objects.equals(u.getInstitutionId(), a.getOwnerInstitutionId());
        boolean partnerAdmin = role(u, "INSTITUTION_ADMIN") && Objects.equals(u.getInstitutionId(), a.getPartnerInstitutionId());
        boolean hasGrant = grants.findByAgreementIdAndUserId(agreementId, u.getId()).stream().anyMatch(g -> "ACTIVE".equals(g.getStatus()));

        if (!system && !ownerAdmin && !partnerAdmin && !hasGrant) {
            throw new SecurityException("You do not have permission to view grants for this agreement");
        }

        return grants.findByAgreementId(agreementId);
    }

    public List<Map<String, Object>> sharedAccess(String email) {
        User u = actor(email);
        return grants.findByUserId(u.getId()).stream()
                .filter(g -> "ACTIVE".equals(g.getStatus()))
                .map(g -> agreements.findById(g.getAgreementId()).orElse(null))
                .filter(Objects::nonNull)
                .filter(a -> "ACTIVE".equals(a.getStatus()) && a.getEndDate().isAfter(LocalDateTime.now()))
                .map(a -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("agreement", a);
                    m.put("equipment", equipment.findById(a.getEquipmentId()).orElse(null));
                    m.put("listing", listings.findByEquipmentId(a.getEquipmentId()).orElse(null));
                    m.put("institution", institutions.findById(a.getOwnerInstitutionId()).map(x -> x.getName()).orElse(""));
                    return m;
                }).toList();
    }

    @Transactional
    public SharingAgreement revokeAgreement(Long id, String reason, String email) {
        requireReason(reason);
        SharingAgreement a = agreements.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Agreement not found"));
        User u = actor(email);
        if (!sys(u) && (!role(u, "INSTITUTION_ADMIN") || !Objects.equals(u.getInstitutionId(), a.getOwnerInstitutionId()))) {
            throw new SecurityException("Owner administrator required");
        }
        a.setStatus("REVOKED");
        a.setRevokedBy(u.getId());
        a.setRevokedAt(LocalDateTime.now());
        a.setRevocationReason(reason.trim());
        for (SharingAccessGrant g : grants.findByAgreementId(id)) {
            g.setStatus("REVOKED");
            g.setRevokedBy(u.getId());
            g.setRevokedAt(LocalDateTime.now());
            g.setRevocationReason(reason.trim());
            grants.save(g);
        }
        cancelBookingsForRevocation(a.getId(), null, reason, u.getEmail());
        SharingAgreement saved = agreements.save(a);
        if (notificationService != null) {
            try {
                List<User> partnerAdmins = users.findByInstitutionIdAndRequestedRoleAndMembershipStatus(
                        saved.getPartnerInstitutionId(), "INSTITUTION_ADMIN", "APPROVED");
                for (User admin : partnerAdmins) {
                    notificationService.createNotification(
                            admin.getId(),
                            com.labresource.platform.notification.model.NotificationType.SHARING_AGREEMENT_REVOKED,
                            "Sharing Agreement Revoked",
                            "Sharing Agreement #" + saved.getId() + " has been revoked. Reason: " + reason.trim(),
                            com.labresource.platform.notification.model.ReferenceType.SHARING_AGREEMENT,
                            saved.getId()
                    );
                }
            } catch (Exception ignored) {
            }
        }
        return saved;
    }

    @Transactional
    public SharingAccessGrant revokeGrant(Long id, String reason, String email) {
        requireReason(reason);
        SharingAccessGrant g = grants.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Grant not found"));
        SharingAgreement a = agreements.findById(g.getAgreementId())
                .orElseThrow(() -> new IllegalArgumentException("Agreement not found"));
        User u = actor(email);
        if (!sys(u) && (!role(u, "INSTITUTION_ADMIN") || !Objects.equals(u.getInstitutionId(), a.getOwnerInstitutionId()))) {
            throw new SecurityException("Owner administrator required");
        }
        g.setStatus("REVOKED");
        g.setRevokedBy(u.getId());
        g.setRevokedAt(LocalDateTime.now());
        g.setRevocationReason(reason.trim());
        cancelBookingsForRevocation(a.getId(), g.getUserId(), reason, u.getEmail());
        return grants.save(g);
    }

    private void cancelBookingsForRevocation(Long agreementId, Long userIdFilter, String reason, String actorEmail) {
        List<Booking> toCancel = bookings.findBySharingAgreementIdAndStatusInAndStartTimeAfter(
                agreementId,
                List.of("PENDING_APPROVAL", "PENDING", "APPROVED", "CONFIRMED"),
                LocalDateTime.now());

        for (Booking b : toCancel) {
            if (userIdFilter == null || Objects.equals(b.getUserId(), userIdFilter)) {
                String prevStatus = b.getStatus();
                b.setStatus("CANCELLED");
                bookings.save(b);

                if (auditLogs != null) {
                    String actorInfo = (actorEmail + " - Reason: " + reason).trim();
                    if (actorInfo.length() > 180) {
                        actorInfo = actorInfo.substring(0, 180);
                    }
                    try {
                        auditLogs.save(new BookingAuditLog(b.getId(), "SHARING_REVOKED", prevStatus, "CANCELLED", actorInfo));
                    } catch (Exception ignored) {}
                }

                if (events != null) {
                    events.publishEvent(new BookingReleasedEvent(b.getEquipmentId(), b.getStartTime(), b.getEndTime()));
                }
            }
        }
    }

    private void requireReason(String s) {
        if (s == null || s.isBlank()) throw new IllegalArgumentException("A reason is required");
    }
}
