package com.auth.service.dto;

import lombok.Data;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Data
public class OtpRequest {

    @NotBlank
    @Email
    private String email;
}
