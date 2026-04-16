package com.auth.service.service;

import com.auth.service.entity.User;
import com.auth.service.entity.VerificationToken;
import com.auth.service.enumClasses.VerificationType;
import com.auth.service.exception.VerificationTokenException;
import com.auth.service.repository.VerificationTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.security.SecureRandom;

@Service
@RequiredArgsConstructor
public class VerificationService {

    private final VerificationTokenRepository tokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public String generateOtp() {
        int otp = 100000 + secureRandom.nextInt(900000);
        return String.valueOf(otp);
    }

    @Transactional
    public VerificationToken createToken(User user, VerificationType type) {
        List<VerificationToken> activeTokens = tokenRepository.findByUserAndTypeAndUsedFalse(user, type);
        activeTokens.forEach(token -> token.setUsed(true));
        tokenRepository.saveAll(activeTokens);

        String otp = generateOtp();

        VerificationToken token = new VerificationToken();
        token.setUser(user);
        token.setToken(otp);
        token.setType(type);
        token.setExpiryDate(LocalDateTime.now().plusMinutes(5));

        return tokenRepository.save(token);
    }

    @Transactional
    public User verifyToken(User user, String tokenValue, VerificationType type) {
        List<VerificationToken> activeTokens = tokenRepository.findByUserAndTypeAndUsedFalse(user, type);
        VerificationToken token = activeTokens.stream()
                .filter(activeToken -> activeToken.getToken().equals(tokenValue))
                .findFirst()
                .orElseThrow(() -> new VerificationTokenException("Invalid verification code"));

        if (token.getExpiryDate().isBefore(LocalDateTime.now())) {
            token.setUsed(true);
            tokenRepository.save(token);
            throw new VerificationTokenException("Verification code expired");
        }

        token.setUsed(true);
        tokenRepository.save(token);

        return token.getUser();
    }
}
