package com.labresource.platform.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UserRegistrationRequest {

    @NotNull(message = "Institution ID is required.")
    private Long institutionId;

    private Long departmentId;

    @Size(max = 150, message = "Full name must not exceed 150 characters.")
    private String fullName;

    @Size(max = 30, message = "Phone number must not exceed 30 characters.")
    private String phoneNumber;

    @Size(max = 50, message = "Requested role must not exceed 50 characters.")
    private String requestedRole;

    @NotBlank(message = "Email is required.")
    @Email(message = "Email must be valid.")
    @Size(max = 180, message = "Email must not exceed 180 characters.")
    private String email;

    @NotBlank(message = "Password is required.")
    @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters.")
    private String password;
}
