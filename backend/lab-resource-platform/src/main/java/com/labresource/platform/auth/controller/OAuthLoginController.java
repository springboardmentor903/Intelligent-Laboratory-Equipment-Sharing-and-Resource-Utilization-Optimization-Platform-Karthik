package com.labresource.platform.auth.controller;

import com.labresource.platform.auth.dto.OAuthCodeExchangeRequest;
import com.labresource.platform.auth.service.JwtService;
import com.labresource.platform.auth.service.OAuthLoginTicketService;
import com.labresource.platform.user.entity.User;
import com.labresource.platform.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth/oauth2")
public class OAuthLoginController {

    private final OAuthLoginTicketService tickets;
    private final UserService users;
    private final JwtService jwtService;

    public OAuthLoginController(OAuthLoginTicketService tickets,
                                UserService users,
                                JwtService jwtService) {
        this.tickets = tickets;
        this.users = users;
        this.jwtService = jwtService;
    }

    @PostMapping("/exchange")
    public ResponseEntity<?> exchange(@Valid @RequestBody OAuthCodeExchangeRequest request) {
        return tickets.consume(request.code())
                .flatMap(users::findById)
                .filter(user -> !Boolean.FALSE.equals(user.getMembershipApproved()))
                .map(this::loginResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                        "code", "OAUTH_CODE_INVALID",
                        "message", "Google sign-in could not be completed. Please try again."
                )));
    }

    private Map<String, Object> loginResponse(User user) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", user.getId());
        response.put("email", user.getEmail());
        response.put("fullName", user.getFullName() != null ? user.getFullName() : "");
        response.put("institutionId", user.getInstitutionId());
        response.put("departmentId", user.getDepartmentId());
        response.put("roles", users.getRoleNames(user.getId()));
        response.put("token", jwtService.generateToken(user));
        response.put("message", "Login successful.");
        return response;
    }
}
