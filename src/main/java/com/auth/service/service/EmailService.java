package com.auth.service.service;

import org.springframework.stereotype.Service;

@Service
public class EmailService {

    public void sendVerificationEmail(String email, String token) {

        String link = "http://localhost:8081/auth/verify?token=" + token;

        System.out.println("Verification Link: " + link);
    }
}
