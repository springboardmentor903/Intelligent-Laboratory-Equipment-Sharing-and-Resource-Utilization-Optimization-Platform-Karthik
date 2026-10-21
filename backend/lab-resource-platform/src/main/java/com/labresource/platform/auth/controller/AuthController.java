
package com.labresource.platform.auth.controller;

import com.labresource.platform.auth.service.JwtService;
import com.labresource.platform.user.dto.UserLoginRequest;
import com.labresource.platform.user.entity.User;
import com.labresource.platform.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import com.labresource.platform.auth.dto.PasswordResetRequest;
import com.labresource.platform.auth.dto.PasswordResetConfirmRequest;
import com.labresource.platform.auth.service.PasswordResetService;
import org.springframework.beans.factory.annotation.Value;
import java.util.HashMap;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;
    private final PasswordResetService passwordResetService;

    @Value("${app.password-reset.expose-token:false}")
    private boolean exposeResetToken;

    public AuthController(
            UserService userService,
            JwtService jwtService,
            PasswordResetService passwordResetService) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @Valid @RequestBody UserLoginRequest request) {

        User user = userService.authenticate(
                request.getEmail(),
                request.getPassword()
        );

        String token = jwtService.generateToken(user);

        Map<String, Object> response = Map.of(
                "id", user.getId(),
                "email", user.getEmail(),
                "institutionId", user.getInstitutionId(),
                "token", token,
                "message", "Login successful."
        );

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
