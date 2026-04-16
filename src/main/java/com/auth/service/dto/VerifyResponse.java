package com.auth.service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class VerifyResponse {
    private String message;
    private String accessToken;
    private String refreshToken;
    private String username;
}
