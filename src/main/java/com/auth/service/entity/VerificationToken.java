package com.auth.service.entity;

import com.auth.service.enumClasses.VerificationType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;


import java.time.LocalDateTime;

@Entity
@Getter
@Setter
public class VerificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String token;

    private LocalDateTime expiryDate;

    @Enumerated(EnumType.STRING)
    private VerificationType type; // EMAIL, OTP

    private boolean used = false;

    @ManyToOne(fetch = FetchType.LAZY)
    private User user;
}
