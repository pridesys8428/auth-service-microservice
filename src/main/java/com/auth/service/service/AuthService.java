package com.auth.service.service;

import com.auth.service.dto.*;
import com.auth.service.entity.Role;
import com.auth.service.entity.User;
import com.auth.service.entity.VerificationToken;
import com.auth.service.enumClasses.VerificationType;
import com.auth.service.exception.DuplicateUserException;
import com.auth.service.exception.EmailVerificationException;
import com.auth.service.exception.UserNotFoundException;
import com.auth.service.repository.RoleRepository;
import com.auth.service.repository.UserRepository;
import com.auth.service.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;


@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final EmailService emailService;
    private final VerificationService verificationService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {

        User existingUser = userRepository.findByEmail(request.getEmail());

//        if (existingUser != null && !existingUser.isVerified()) {
//            throw new EmailVerificationException("Verify your email first");
//        }

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateUserException("userid already exist");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateUserException("email already exist");
        }

        User user = new User();

        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEnabled(true);
        user.setVerified(false);

        Role role = roleRepository.findByName("USER").orElseThrow();
        user.setRoles(Collections.singleton(role));

        userRepository.save(user);
        createVerificationToken(user);

        return new AuthResponse(null, null, "Verification email sent");
    }

    public void createVerificationToken(User user) {

        VerificationToken token = verificationService
                .createToken(user, VerificationType.EMAIL);

        emailService.sendOtpEmail(user.getEmail(), token.getToken());
    }

    public VerifyResponse verifyEmail(VerifyRequest request) {

        User user = userRepository
                .findByEmail(request.getEmail());

        if (user == null) {
            throw new UserNotFoundException("User Not Found");
        }

        User verifiedUser = verificationService.verifyToken(
                user,
                request.getVerificationToken(),
                VerificationType.EMAIL);

        verifiedUser.setVerified(true);
        userRepository.save(verifiedUser);

        String accessToken = jwtTokenProvider.generateToken(verifiedUser.getUsername());
        String refreshToken = refreshTokenService
                .createRefreshToken(verifiedUser)
                .getToken();

        return new VerifyResponse("Email Verification Successful", accessToken, refreshToken, verifiedUser.getUsername());
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {

        User user = userRepository
                .findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("Invalid username or password"));

        if (!passwordEncoder.matches(
                request.getPassword(),
                user.getPassword())) {

            throw new RuntimeException("Invalid username or password");
        }

        if (!user.isVerified()) {
            createVerificationToken(user);
            throw new EmailVerificationException("Please verify your email first, a verification email has been sent to your registered email");
        }

        String accessToken = jwtTokenProvider.generateToken(user.getUsername());
        String refreshToken = refreshTokenService
                .createRefreshToken(user)
                .getToken();

        return new AuthResponse(accessToken, refreshToken, user.getUsername());
    }

//    public AuthResponse login(LoginRequest request) {
//
//        User user = userRepository
//                .findByUsername(request.getUsername())
//                .orElseThrow();
//
//        if (!user.isVerified()) {
//            throw new EmailVerificationException("Please verify your email first");
//        }
//
//        createVerificationToken(user);
//
//        if (!passwordEncoder.matches(
//                request.getPassword(),
//                user.getPassword())) {
//
//            throw new RuntimeException("Invalid credentials");
//        }
//
//        String accessToken = jwtTokenProvider.generateToken(user.getUsername());
//        String refreshToken = refreshTokenService
//                .createRefreshToken(user)
//                .getToken();
//
//        return new AuthResponse(accessToken, refreshToken, user.getUsername());
//    }

    public AuthResponse refreshToken(String refreshToken) {

        User user = refreshTokenService
                .verifyExpiration(refreshTokenService.getByToken(refreshToken))
                .getUser();

        String accessToken = jwtTokenProvider.generateToken(user.getUsername());
        String newRefreshToken = refreshTokenService
                .createRefreshToken(user)
                .getToken();

        return new AuthResponse(accessToken, newRefreshToken, user.getUsername());
    }

    @Transactional
    public void sendOTP(OtpRequest request) {
        User user = userRepository
                .findByEmail(request.getEmail());

        if(user == null) {
            throw new UserNotFoundException("User Not Found");
        }

        createVerificationToken(user);
    }
}
