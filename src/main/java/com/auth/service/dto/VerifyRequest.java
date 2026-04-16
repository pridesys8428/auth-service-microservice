package com.auth.service.dto;

import lombok.Data;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Data
public class VerifyRequest {

    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String verificationToken;
}
