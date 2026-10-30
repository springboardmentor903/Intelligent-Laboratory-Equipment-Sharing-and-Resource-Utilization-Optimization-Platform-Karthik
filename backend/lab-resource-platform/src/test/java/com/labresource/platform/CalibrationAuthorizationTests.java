package com.labresource.platform;

import com.labresource.platform.calibration.controller.CalibrationRecordController;
import com.labresource.platform.calibration.entity.CalibrationRecord;
import com.labresource.platform.calibration.service.CalibrationRecordService;
import com.labresource.platform.common.security.ResourceAccessPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(CalibrationAuthorizationTests.TestConfig.class)
class CalibrationAuthorizationTests {
    @Autowired private CalibrationRecordController controller;
    @Autowired private CalibrationRecordService service;
    @Autowired private ResourceAccessPolicy resourceAccess;

    @AfterEach
    void clearContext() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        reset(service, resourceAccess);
    }

    @Test
    void noAuthenticationIsRejectedBeforeServiceCall() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        assertThrows(AuthenticationCredentialsNotFoundException.class,
                () -> controller.create(15L, new CalibrationRecord()));
        verifyNoInteractions(service);
    }

    @Test
    void researcherTokenIsAuthenticatedButCannotCreateCalibration() {
        authenticate("RESEARCHER_STUDENT");
        assertThrows(AccessDeniedException.class,
                () -> controller.create(15L, new CalibrationRecord()));
        verifyNoInteractions(service);
    }

    @Test
    void departmentHeadCannotCreateCalibration() {
        authenticate("DEPARTMENT_HEAD");
        assertThrows(AccessDeniedException.class,
                () -> controller.create(15L, new CalibrationRecord()));
        verifyNoInteractions(service);
    }

    @Test
    void everyExistingCalibrationCreatorRoleCanProceedInOwnInstitution() {
        for (String role : List.of("LAB_TECHNICIAN", "LAB_MANAGER",
                "INSTITUTION_ADMIN", "SYSTEM_ADMIN")) {
            reset(service, resourceAccess);
            authenticate(role);
            when(resourceAccess.canAccessEquipment(eq(15L), any())).thenReturn(true);
            controller.create(15L, new CalibrationRecord());
            verify(service).create(eq(15L), any(CalibrationRecord.class));
        }
    }

    private void authenticate(String role) {
        var authentication = new UsernamePasswordAuthenticationToken(
                "person@example.test", "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        org.springframework.security.core.context.SecurityContextHolder.getContext()
                .setAuthentication(authentication);
    }

    @Configuration
    @EnableMethodSecurity
    static class TestConfig {
        @Bean CalibrationRecordService calibrationRecordService() {
            return mock(CalibrationRecordService.class);
        }
        @Bean ResourceAccessPolicy resourceAccess() {
            return mock(ResourceAccessPolicy.class);
        }
        @Bean CalibrationRecordController controller(
                CalibrationRecordService service, ResourceAccessPolicy policy) {
            return new CalibrationRecordController(service);
        }
    }
}
