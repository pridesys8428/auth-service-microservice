package com.auth.service.service;

import com.auth.service.entity.User;
import com.auth.service.entity.VerificationToken;
import com.auth.service.enumClasses.VerificationType;
import com.auth.service.repository.VerificationTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VerificationService {

    private final VerificationTokenRepository tokenRepository;

    public VerificationToken createToken(User user, VerificationType type) {

        // invalidate old tokens
        List<VerificationToken> tokens =
                tokenRepository.findByUserAndTypeAndUsedFalse(user, type);

        tokens.forEach(t -> t.setUsed(true));
        tokenRepository.saveAll(tokens);

        VerificationToken token = new VerificationToken();
        token.setUser(user);
        token.setType(type);
        token.setToken(UUID.randomUUID().toString());
        token.setExpiryDate(LocalDateTime.now().plusMinutes(2));
        token.setUsed(false);

        return tokenRepository.save(token);
    }

    public User verifyToken(String tokenValue, VerificationType type) {

        VerificationToken token = tokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new RuntimeException("Invalid token"));

        if (token.isUsed()) {
            throw new RuntimeException("Token already used");
        }

        if (!token.getType().equals(type)) {
            throw new RuntimeException("Invalid token type");
        }

        if (token.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Token expired");
        }

        token.setUsed(true);
        tokenRepository.save(token);

        return token.getUser();
    }
}
