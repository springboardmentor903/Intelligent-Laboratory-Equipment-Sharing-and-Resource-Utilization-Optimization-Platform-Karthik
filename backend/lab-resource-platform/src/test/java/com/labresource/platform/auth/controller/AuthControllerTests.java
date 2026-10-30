package com.labresource.platform.auth.controller;

import com.labresource.platform.auth.service.JwtService;
import com.labresource.platform.auth.service.PasswordResetService;
import com.labresource.platform.auth.service.PasswordResetDeliveryService;
import com.labresource.platform.user.dto.UserLoginRequest;
import com.labresource.platform.user.entity.User;
import com.labresource.platform.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTests {

    @Mock private UserService userService;
    @Mock private JwtService jwtService;
    @Mock private PasswordResetService passwordResetService;
    @Mock private PasswordResetDeliveryService passwordResetDeliveryService;

    @InjectMocks private AuthController controller;

    @Test
    void loginReturnsDatabaseLoadedRolesForAuthenticatedUser() {
        User user = new User();
        user.setId(17L);
        user.setEmail("researcher@example.test");
        user.setInstitutionId(9L);

        UserLoginRequest request = new UserLoginRequest();
        request.setEmail(user.getEmail());
        request.setPassword("test-only-password");

        when(userService.authenticate(user.getEmail(), "test-only-password"))
                .thenReturn(user);
        when(userService.getRoleNames(user.getId()))
                .thenReturn(List.of("LAB_MANAGER", "RESEARCHER_STUDENT"));
        when(jwtService.generateToken(user)).thenReturn("test-only-token");

        Map<String, Object> body = controller.login(request).getBody();

        assertEquals(List.of("LAB_MANAGER", "RESEARCHER_STUDENT"), body.get("roles"));
        assertEquals(user.getId(), body.get("id"));
        assertFalse(body.containsKey("password"));
        assertFalse(body.containsKey("passwordHash"));
        verify(userService).getRoleNames(user.getId());
    }

    @Test
    void pendingMembershipCannotReceiveRolesOrJwt() {
        User user = new User();
        user.setId(18L);
        user.setEmail("pending@example.test");
        user.setInstitutionId(9L);
        user.setMembershipApproved(false);
        UserLoginRequest request = new UserLoginRequest();
        request.setEmail(user.getEmail());
        request.setPassword("test-only-password");
        when(userService.authenticate(user.getEmail(), "test-only-password"))
                .thenReturn(user);

        var response = controller.login(request);

        assertEquals(403, response.getStatusCode().value());
        assertEquals("MEMBERSHIP_PENDING", response.getBody().get("code"));
        assertFalse(response.getBody().containsKey("token"));
        verify(userService, never()).getRoleNames(user.getId());
        verifyNoInteractions(jwtService);
    }
}
