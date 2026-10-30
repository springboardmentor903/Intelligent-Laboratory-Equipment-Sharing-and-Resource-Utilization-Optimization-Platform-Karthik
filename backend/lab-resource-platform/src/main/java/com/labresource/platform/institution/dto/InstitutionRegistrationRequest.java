package com.labresource.platform.institution.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class InstitutionRegistrationRequest {

    @NotBlank(message = "Institution name is required.")
    @Size(max = 150, message = "Institution name must not exceed 150 characters.")
    @JsonAlias({"institutionName", "name"})
    private String name;

    @Size(max = 255, message = "Address must not exceed 255 characters.")
    private String address;

    @Size(max = 150, message = "Location must not exceed 150 characters.")
    private String location;

    @Email(message = "Contact email must be valid.")
    @Size(max = 180, message = "Contact email must not exceed 180 characters.")
    private String contactEmail;

    @Size(max = 30, message = "Phone number must not exceed 30 characters.")
    private String phoneNumber;

    @NotBlank(message = "Admin full name is required.")
    @Size(max = 150, message = "Admin full name must not exceed 150 characters.")
    private String adminFullName;

    @NotBlank(message = "Admin email is required.")
    @Email(message = "Admin email must be a valid email address.")
    @Size(max = 180, message = "Admin email must not exceed 180 characters.")
    private String adminEmail;

    @NotBlank(message = "Admin password is required.")
    @Size(min = 8, max = 72, message = "Admin password must be between 8 and 72 characters.")
    private String adminPassword;

    @Size(max = 30, message = "Admin phone number must not exceed 30 characters.")
    private String adminPhoneNumber;
}
