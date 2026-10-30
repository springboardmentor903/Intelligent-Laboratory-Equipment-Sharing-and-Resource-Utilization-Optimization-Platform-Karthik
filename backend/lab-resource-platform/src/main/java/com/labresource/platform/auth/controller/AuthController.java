package com.labresource.platform.auth.controller;

import com.labresource.platform.auth.service.JwtService;
import com.labresource.platform.user.dto.UserLoginRequest;
import com.labresource.platform.user.entity.User;
import com.labresource.platform.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import com.labresource.platform.auth.dto.PasswordResetRequest;
import com.labresource.platform.auth.dto.PasswordResetConfirmRequest;
import com.labresource.platform.auth.service.PasswordResetService;
import com.labresource.platform.auth.service.PasswordResetDeliveryService;
import org.springframework.beans.factory.annotation.Value;
import java.util.HashMap;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;
    private final PasswordResetService passwordResetService;
    private final PasswordResetDeliveryService passwordResetDeliveryService;

    @Value("${app.password-reset.expose-token:false}")
    private boolean exposeResetToken;

    public AuthController(
            UserService userService,
            JwtService jwtService,
            PasswordResetService passwordResetService,
            PasswordResetDeliveryService passwordResetDeliveryService) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.passwordResetService = passwordResetService;
        this.passwordResetDeliveryService = passwordResetDeliveryService;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @Valid @RequestBody UserLoginRequest request) {

        User user = userService.authenticate(
                request.getEmail(),
                request.getPassword()
        );

        if (Boolean.FALSE.equals(user.getMembershipApproved())) {
            if ("REJECTED".equalsIgnoreCase(user.getMembershipStatus())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                        "code", "MEMBERSHIP_REJECTED",
                        "message", "Your institution membership request was rejected by the administrator."
                ));
            }
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "code", "MEMBERSHIP_PENDING",
                    "message", "Your institution membership is awaiting administrator approval."
            ));
        }

        var roles = userService.getRoleNames(user.getId());
        String token = jwtService.generateToken(user);

        Map<String, Object> response = new HashMap<>();
        response.put("id", user.getId());
        response.put("email", user.getEmail());
        response.put("fullName", user.getFullName() != null ? user.getFullName() : "");
        response.put("institutionId", user.getInstitutionId());
        response.put("departmentId", user.getDepartmentId());
        response.put("roles", roles);
        response.put("token", token);
        response.put("message", "Login successful.");

        return ResponseEntity.ok(response);
    }

    @PostMapping("/password-reset/request")
    public ResponseEntity<Map<String, Object>> requestPasswordReset(
            @Valid @RequestBody PasswordResetRequest request) {

        String token = null;

        try {
            token = passwordResetService.createResetToken(request.getEmail());
        } catch (IllegalArgumentException ignored) {
            // Return a generic response to avoid revealing account existence.
        }

        Map<String, Object> response = new HashMap<>();
        response.put("message",
                "If an account exists for that email, password reset instructions will be sent.");

        if (token != null) {
            passwordResetDeliveryService.deliver(request.getEmail().trim().toLowerCase(), token);
        }

        // Only expose the token when explicitly enabled for local development.
        if (exposeResetToken && token != null) {
            response.put("resetToken", token);
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<Map<String, String>> confirmPasswordReset(
            @Valid @RequestBody PasswordResetConfirmRequest request) {

        passwordResetService.resetPassword(
                request.getToken(),
                request.getNewPassword());

        return ResponseEntity.ok(Map.of(
                "message", "Password reset successful. You can now log in with your new password."
        ));
    }
}
