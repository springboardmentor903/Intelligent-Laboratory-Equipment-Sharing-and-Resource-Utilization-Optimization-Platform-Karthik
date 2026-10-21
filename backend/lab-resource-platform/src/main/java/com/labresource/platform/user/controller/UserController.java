
package com.labresource.platform.user.controller;

import com.labresource.platform.user.dto.UserRegistrationRequest;
import com.labresource.platform.user.entity.User;
import com.labresource.platform.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(
            @Valid @RequestBody UserRegistrationRequest request) {

        User user = userService.register(request);

        Map<String, Object> response = Map.of(
                "id", user.getId(),
                "email", user.getEmail(),
                "institutionId", user.getInstitutionId(),
                "message", "Registration successful"
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/email-exists")
    public ResponseEntity<Map<String, Boolean>> emailExists(
            @RequestParam String email) {

        boolean exists = userService.emailExists(email);
        return ResponseEntity.ok(Map.of("exists", exists));
    }
}
