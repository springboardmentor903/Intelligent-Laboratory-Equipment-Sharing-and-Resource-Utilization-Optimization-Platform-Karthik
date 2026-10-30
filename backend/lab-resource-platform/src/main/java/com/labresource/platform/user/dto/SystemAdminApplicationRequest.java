package com.labresource.platform.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SystemAdminApplicationRequest {

    @NotBlank(message = "Full name is required.")
    @Size(max = 150, message = "Full name must not exceed 150 characters.")
    private String fullName;

    @NotBlank(message = "Email is required.")
    @Email(message = "Email must be valid.")
    @Size(max = 180, message = "Email must not exceed 180 characters.")
    private String email;

    @Size(max = 30, message = "Phone number must not exceed 30 characters.")
    private String phoneNumber;

    @NotBlank(message = "Password is required.")
    @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters.")
    private String password;
}
