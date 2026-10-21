
package com.labresource.platform.auth.service;

import com.labresource.platform.auth.entity.PasswordResetToken;
import com.labresource.platform.auth.repository.PasswordResetTokenRepository;
import com.labresource.platform.user.entity.User;
import com.labresource.platform.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@Service
public class PasswordResetService {

    private static final long RESET_TOKEN_VALIDITY_SECONDS = 900;

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final SecureRandom secureRandom = new SecureRandom();
    private final BCryptPasswordEncoder passwordEncoder =
            new BCryptPasswordEncoder();

    public PasswordResetService(
            PasswordResetTokenRepository tokenRepository,
            UserRepository userRepository) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public String createResetToken(String email) {
        User user = userRepository
                .findByEmail(email.trim().toLowerCase())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Unable to process password reset request."));

        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);

        String rawToken = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);

        String tokenHash = sha256(rawToken);

        tokenRepository.deleteByUserId(user.getId());

        PasswordResetToken resetToken = new PasswordResetToken(
                user.getId(),
                tokenHash,
                Instant.now().plusSeconds(
                        RESET_TOKEN_VALIDITY_SECONDS)
        );

        tokenRepository.save(resetToken);

        return rawToken;
    }


    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("Reset token is required.");
        }

        if (newPassword == null
                || newPassword.length() < 8
                || newPassword.length() > 72) {
            throw new IllegalArgumentException(
                    "Password must be between 8 and 72 characters.");
        }

        String tokenHash = sha256(rawToken);

        PasswordResetToken resetToken = tokenRepository
                .findByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Invalid or expired reset token."));

        if (resetToken.isUsed()
                || !resetToken.getExpiresAt().isAfter(Instant.now())) {
            throw new IllegalArgumentException(
                    "Invalid or expired reset token.");
        }

        User user = userRepository.findById(resetToken.getUserId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Invalid or expired reset token."));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        resetToken.setUsed(true);
        tokenRepository.save(resetToken);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    value.getBytes(StandardCharsets.UTF_8));

            return java.util.HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable.", exception);
        }
    }
}
