package com.labresource.platform.sharing;

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
import com.labresource.platform.sharing.service.SharingWorkflowService;
import com.labresource.platform.user.entity.Role;
import com.labresource.platform.user.entity.User;
import com.labresource.platform.user.entity.UserRole;
import com.labresource.platform.user.repository.RoleRepository;
import com.labresource.platform.user.repository.UserRepository;
import com.labresource.platform.user.repository.UserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SharingWorkflowServiceTests {

    @Mock private SharingListingRepository listings;
    @Mock private SharingRequestRepository requests;
    @Mock private SharingAgreementRepository agreements;
    @Mock private SharingAccessGrantRepository grants;
    @Mock private EquipmentRepository equipment;
    @Mock private UserRepository users;
    @Mock private InstitutionRepository institutions;
    @Mock private UserRoleRepository userRoles;
    @Mock private RoleRepository roles;
    @Mock private BookingRepository bookings;
    @Mock private ApplicationEventPublisher events;
    @Mock private BookingAuditLogRepository auditLogs;

    private SharingWorkflowService service;

    private User requester;
    private Role researcherRole;
    private Role instAdminRole;
    private Equipment targetEquipment;
    private SharingListing activeListing;

    @BeforeEach
    void setUp() {
        service = new SharingWorkflowService(
                listings, requests, agreements, grants, equipment,
                users, institutions, userRoles, roles, bookings, events, auditLogs
        );

        requester = new User();
        requester.setId(10L);
        requester.setEmail("researcher@partner.edu");
        requester.setInstitutionId(2L);
        requester.setMembershipStatus("APPROVED");

        researcherRole = new Role("RESEARCHER_STUDENT", "Researcher");
        researcherRole.setId((short) 1);

        instAdminRole = new Role("INSTITUTION_ADMIN", "Institution Admin");
        instAdminRole.setId((short) 5);

        targetEquipment = new Equipment();
        targetEquipment.setId(100L);
        targetEquipment.setName("Mass Spectrometer");
        targetEquipment.setInstitutionId(1L); // Owned by institution 1
        targetEquipment.setShareable(true);

        activeListing = new SharingListing();
        activeListing.setEquipmentId(100L);
        activeListing.setActive(true);
        activeListing.setFeePerHour(new BigDecimal("150.00"));
        activeListing.setExternalSharePercentage(new BigDecimal("30.00"));
        activeListing.setMinimumNoticeHours(24);
        activeListing.setMaximumBookingHours(8);
    }

    @Test
    void submitRequiresActiveInstitutionAdminForRequesterInstitution() {
        when(users.findByEmail("researcher@partner.edu")).thenReturn(Optional.of(requester));
        when(roles.findByName("INSTITUTION_ADMIN")).thenReturn(Optional.of(instAdminRole));
        when(roles.findById((short) 1)).thenReturn(Optional.of(researcherRole));

        UserRole ur = new UserRole(10L, (short) 1);
        when(userRoles.findByUserId(10L)).thenReturn(List.of(ur));

        // No active institution admin found for institution 2
        when(userRoles.findByRoleId((short) 5)).thenReturn(List.of());

        SharingWorkflowService.RequestInput input = new SharingWorkflowService.RequestInput(
                100L,
                "Nanomaterial analysis",
                LocalDateTime.now().plusDays(2),
                LocalDateTime.now().plusDays(5)
        );

        assertThatThrownBy(() -> service.submit(input, "researcher@partner.edu"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active institution administrator");
    }

    @Test
    void submitSucceedsWhenActiveInstitutionAdminExists() {
        when(users.findByEmail("researcher@partner.edu")).thenReturn(Optional.of(requester));
        when(roles.findByName("INSTITUTION_ADMIN")).thenReturn(Optional.of(instAdminRole));
        when(roles.findById((short) 1)).thenReturn(Optional.of(researcherRole));

        UserRole ur = new UserRole(10L, (short) 1);
        when(userRoles.findByUserId(10L)).thenReturn(List.of(ur));

        User instAdmin = new User();
        instAdmin.setId(99L);
        instAdmin.setInstitutionId(2L);
        instAdmin.setMembershipStatus("APPROVED");
        UserRole adminUr = new UserRole(99L, (short) 5);

        when(userRoles.findByRoleId((short) 5)).thenReturn(List.of(adminUr));
        when(users.findById(99L)).thenReturn(Optional.of(instAdmin));

        when(equipment.findByIdForBooking(100L)).thenReturn(Optional.of(targetEquipment));
        when(listings.findByEquipmentId(100L)).thenReturn(Optional.of(activeListing));
        when(requests.hasOpenOverlap(eq(100L), any(), any())).thenReturn(false);
        when(requests.save(any(SharingRequest.class))).thenAnswer(invocation -> {
            SharingRequest saved = invocation.getArgument(0);
            saved.setId(50L);
            return saved;
        });

        SharingWorkflowService.RequestInput input = new SharingWorkflowService.RequestInput(
                100L,
                "Nanomaterial analysis",
                LocalDateTime.now().plusDays(2),
                LocalDateTime.now().plusDays(5)
        );

        SharingRequest result = service.submit(input, "researcher@partner.edu");
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(50L);
        assertThat(result.getRequesterInstitutionId()).isEqualTo(2L);
        assertThat(result.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void revokeAgreementCancelsFutureBookingsWithAuditLogAndPublishesEvent() {
        User ownerAdmin = new User();
        ownerAdmin.setId(1L);
        ownerAdmin.setEmail("admin@owner.edu");
        ownerAdmin.setInstitutionId(1L);

        when(users.findByEmail("admin@owner.edu")).thenReturn(Optional.of(ownerAdmin));
        when(userRoles.findByUserId(1L)).thenReturn(List.of(new UserRole(1L, (short) 5)));
        when(roles.findById((short) 5)).thenReturn(Optional.of(instAdminRole));

        SharingAgreement agreement = new SharingAgreement();
        agreement.setId(77L);
        agreement.setOwnerInstitutionId(1L);
        agreement.setPartnerInstitutionId(2L);
        agreement.setEquipmentId(100L);
        agreement.setStatus("ACTIVE");
        agreement.setEndDate(LocalDateTime.now().plusMonths(1));

        when(agreements.findById(77L)).thenReturn(Optional.of(agreement));
        when(agreements.save(any(SharingAgreement.class))).thenAnswer(i -> i.getArgument(0));

        SharingAccessGrant grant = new SharingAccessGrant();
        grant.setId(88L);
        grant.setAgreementId(77L);
        grant.setUserId(10L);
        grant.setStatus("ACTIVE");
        when(grants.findByAgreementId(77L)).thenReturn(List.of(grant));

        Booking futureBooking = new Booking();
        futureBooking.setId(500L);
        futureBooking.setEquipmentId(100L);
        futureBooking.setUserId(10L);
        futureBooking.setStatus("CONFIRMED");
        futureBooking.setSharingAgreementId(77L);
        futureBooking.setStartTime(LocalDateTime.now().plusDays(3));
        futureBooking.setEndTime(LocalDateTime.now().plusDays(3).plusHours(2));

        when(bookings.findBySharingAgreementIdAndStatusInAndStartTimeAfter(eq(77L), anyList(), any()))
                .thenReturn(List.of(futureBooking));

        service.revokeAgreement(77L, "Partnership terminated by mutual agreement", "admin@owner.edu");

        assertThat(agreement.getStatus()).isEqualTo("REVOKED");
        assertThat(agreement.getRevocationReason()).isEqualTo("Partnership terminated by mutual agreement");
        assertThat(grant.getStatus()).isEqualTo("REVOKED");

        assertThat(futureBooking.getStatus()).isEqualTo("CANCELLED");
        verify(bookings).save(futureBooking);

        ArgumentCaptor<BookingAuditLog> auditCaptor = ArgumentCaptor.forClass(BookingAuditLog.class);
        verify(auditLogs).save(auditCaptor.capture());
        BookingAuditLog audit = auditCaptor.getValue();
        assertThat(audit.getBookingId()).isEqualTo(500L);
        assertThat(audit.getEventType()).isEqualTo("SHARING_REVOKED");
        assertThat(audit.getPreviousStatus()).isEqualTo("CONFIRMED");
        assertThat(audit.getNewStatus()).isEqualTo("CANCELLED");
        assertThat(audit.getActorEmail()).contains("Partnership terminated");

        verify(events).publishEvent(any(BookingReleasedEvent.class));
    }

    @Test
    void revokeGrantCancelsOnlyTargetUserFutureBookingsWithAuditLog() {
        User ownerAdmin = new User();
        ownerAdmin.setId(1L);
        ownerAdmin.setEmail("admin@owner.edu");
        ownerAdmin.setInstitutionId(1L);

        when(users.findByEmail("admin@owner.edu")).thenReturn(Optional.of(ownerAdmin));
        when(userRoles.findByUserId(1L)).thenReturn(List.of(new UserRole(1L, (short) 5)));
        when(roles.findById((short) 5)).thenReturn(Optional.of(instAdminRole));

        SharingAgreement agreement = new SharingAgreement();
        agreement.setId(77L);
        agreement.setOwnerInstitutionId(1L);
        agreement.setStatus("ACTIVE");

        SharingAccessGrant grant = new SharingAccessGrant();
        grant.setId(88L);
        grant.setAgreementId(77L);
        grant.setUserId(10L);
        grant.setStatus("ACTIVE");

        when(grants.findById(88L)).thenReturn(Optional.of(grant));
        when(agreements.findById(77L)).thenReturn(Optional.of(agreement));

        Booking userBooking = new Booking();
        userBooking.setId(501L);
        userBooking.setEquipmentId(100L);
        userBooking.setUserId(10L);
        userBooking.setStatus("PENDING_APPROVAL");
        userBooking.setSharingAgreementId(77L);
        userBooking.setStartTime(LocalDateTime.now().plusDays(2));
        userBooking.setEndTime(LocalDateTime.now().plusDays(2).plusHours(3));

        Booking otherUserBooking = new Booking();
        otherUserBooking.setId(502L);
        otherUserBooking.setEquipmentId(100L);
        otherUserBooking.setUserId(11L);
        otherUserBooking.setStatus("APPROVED");
        otherUserBooking.setSharingAgreementId(77L);
        otherUserBooking.setStartTime(LocalDateTime.now().plusDays(2));
        otherUserBooking.setEndTime(LocalDateTime.now().plusDays(2).plusHours(3));

        when(bookings.findBySharingAgreementIdAndStatusInAndStartTimeAfter(eq(77L), anyList(), any()))
                .thenReturn(List.of(userBooking, otherUserBooking));

        service.revokeGrant(88L, "Safety training expired", "admin@owner.edu");

        assertThat(grant.getStatus()).isEqualTo("REVOKED");
        assertThat(grant.getRevocationReason()).isEqualTo("Safety training expired");

        assertThat(userBooking.getStatus()).isEqualTo("CANCELLED");
        assertThat(otherUserBooking.getStatus()).isEqualTo("APPROVED"); // Not cancelled for other user

        verify(bookings).save(userBooking);
        verify(bookings, never()).save(otherUserBooking);

        ArgumentCaptor<BookingAuditLog> auditCaptor = ArgumentCaptor.forClass(BookingAuditLog.class);
        verify(auditLogs).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getBookingId()).isEqualTo(501L);
        assertThat(auditCaptor.getValue().getPreviousStatus()).isEqualTo("PENDING_APPROVAL");

        verify(events).publishEvent(any(BookingReleasedEvent.class));
    }
}
