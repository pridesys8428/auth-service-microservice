package com.auth.service.repository;

import com.auth.service.entity.User;
import com.auth.service.entity.VerificationToken;
import com.auth.service.enumClasses.VerificationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {

    Optional<VerificationToken> findByToken(String token);

    List<VerificationToken> findByUserAndTypeAndUsedFalse(User user, VerificationType type);
}
