package com.auth.service.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ErrorResponse {
    String timestamp;
    int status;
    String error;
    String message;
    String path;
}
