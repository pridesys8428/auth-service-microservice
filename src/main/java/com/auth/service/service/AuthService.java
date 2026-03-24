package com.auth.service.service;

import com.auth.service.dto.AuthResponse;
import com.auth.service.dto.LoginRequest;
import com.auth.service.dto.RegisterRequest;
import com.auth.service.entity.Role;
import com.auth.service.entity.User;
import com.auth.service.exception.DuplicateUserException;
import com.auth.service.exception.EmailVerificationException;
import com.auth.service.repository.RoleRepository;
import com.auth.service.repository.UserRepository;
import com.auth.service.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collections;


@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;

    public AuthResponse register(RegisterRequest request) {

        User user = userRepository.findByEmail(request.getEmail());

        if(user != null && !user.isVerified()){
            throw new EmailVerificationException("Verify Your Email Address");
        }

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateUserException("userid already exist");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateUserException("email already exist");
        }

        if(user == null){
            user = new User();
        }

        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEnabled(true);

        Role role = roleRepository.findByName("USER")
                .orElseThrow();

        user.setRoles(Collections.singleton(role));

        userRepository.save(user);

        String accessToken = jwtTokenProvider.generateToken(user.getUsername());
        String refreshToken = refreshTokenService
                .createRefreshToken(user)
                .getToken();

        return new AuthResponse(accessToken, refreshToken, user.getUsername());
    }

    public AuthResponse login(LoginRequest request) {

        User user = userRepository
                .findByUsername(request.getUsername())
                .orElseThrow();

        if (!passwordEncoder.matches(
                request.getPassword(),
                user.getPassword())) {

            throw new RuntimeException("Invalid credentials");
        }

        String accessToken = jwtTokenProvider.generateToken(user.getUsername());
        String refreshToken = refreshTokenService
                .createRefreshToken(user)
                .getToken();

        return new AuthResponse(accessToken, refreshToken, user.getUsername());
    }

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
}
