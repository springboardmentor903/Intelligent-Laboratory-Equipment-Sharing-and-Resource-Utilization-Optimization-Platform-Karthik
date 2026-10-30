package com.labresource.platform.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OAuthCodeExchangeRequest(
        @NotBlank @Size(max = 64) String code) { }
