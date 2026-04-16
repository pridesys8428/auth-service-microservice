package com.auth.service.service;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    public void sendOtpEmail(String email, String otp) {

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("Your OTP Code");

        message.setText(
                "Your verification OTP is: " + otp + "\n\n" +
                        "This OTP will expire in 5 minutes.\n\n" +
                        "Do not share this code with anyone."
        );

        mailSender.send(message);
    }

    public void sendVerificationEmail(String email, String token) {

        String link = "http://localhost:8081/auth/verify?token=" + token;

        System.out.println("Verification Link: " + link);
    }
}
