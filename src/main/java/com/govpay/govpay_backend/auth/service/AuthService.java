package com.govpay.govpay_backend.auth.service;

import com.govpay.govpay_backend.auth.config.JwtProperties;
import com.govpay.govpay_backend.auth.dto.AuthDto.*;
import com.govpay.govpay_backend.auth.entity.RefreshToken;
import com.govpay.govpay_backend.auth.entity.User;
import com.govpay.govpay_backend.auth.repository.RefreshTokenRepository;
import com.govpay.govpay_backend.auth.repository.UserRepository;
import com.govpay.govpay_backend.auth.security.JwtService;
import com.govpay.govpay_backend.common.exception.GovPayException;
import com.govpay.govpay_backend.notification.dto.NotificationEvents.UserRegisteredEvent;
import com.govpay.govpay_backend.notification.publisher.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.time.Instant;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final EventPublisher eventPublisher;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail().toLowerCase())) {
            throw new UserAlreadyExistsException(request.getEmail());
        }

        User user = User.builder()
                .email(request.getEmail().toLowerCase().trim())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName().trim())
                .phoneNumber(request.getPhoneNumber())
                .role(User.Role.USER)
                .status(User.UserStatus.ACTIVE)
                .kycStatus(User.KycStatus.PENDING)
                .build();

        user = userRepository.save(user);
        log.info("New user registered: {}", user.getEmail());

        // Publish event for notification service to pick up
        eventPublisher.publishUserRegistered(new UserRegisteredEvent(
                user.getId(), user.getEmail(), user.getFirstName(), user.getLastName()
        ));

        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        // Spring Security handles bad credentials — throws BadCredentialsException
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail().toLowerCase(),
                        request.getPassword()
                )
        );

        User user = userRepository.findByEmail(request.getEmail().toLowerCase())
                .orElseThrow(() -> new InvalidCredentialsException());

        if (!user.isActive()) {
            throw new AccountDisabledException();
        }

        // Revoke all existing refresh tokens on new login (token rotation)
        refreshTokenRepository.revokeAllUserTokens(user);

        log.info("User logged in: {}", user.getEmail());
        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        RefreshToken stored = refreshTokenRepository.findByToken(request.getRefreshToken())
                .orElseThrow(() -> new InvalidTokenException("token not found"));

        if (!stored.isValid()) {
            // If token is already compromised/expired, revoke all tokens for this user
            // This detects refresh token reuse attacks
            refreshTokenRepository.revokeAllUserTokens(stored.getUser());
            throw new InvalidTokenException("token is expired or revoked");
        }

        // Rotate: revoke old token, issue new pair
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        User user = stored.getUser();
        log.debug("Refresh token rotated for user: {}", user.getEmail());
        return buildAuthResponse(user);
    }

    @Transactional
    public void logout(String userEmail) {
        userRepository.findByEmail(userEmail).ifPresent(user -> {
            refreshTokenRepository.revokeAllUserTokens(user);
            log.info("User logged out, all tokens revoked: {}", userEmail);
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private AuthResponse buildAuthResponse(User user) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());

        String accessToken = jwtService.generateAccessToken(userDetails, Map.of(
                "userId", user.getId().toString(),
                "role", user.getRole().name()
        ));

        String rawRefreshToken = jwtService.generateRefreshToken();

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(rawRefreshToken)
                .expiresAt(Instant.now().plusMillis(jwtProperties.getRefreshTokenExpiryMs()))
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshToken);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtProperties.getAccessTokenExpiryMs() / 1000)
                .user(UserInfo.from(user))
                .build();
    }

    // ── Inner exceptions (auth-specific) ──────────────────────────────────────

    @ResponseStatus(HttpStatus.CONFLICT)
    public static class UserAlreadyExistsException extends GovPayException {
        public UserAlreadyExistsException(String email) {
            super("User already exists with email: " + email);
        }
    }

    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public static class InvalidCredentialsException extends GovPayException {
        public InvalidCredentialsException() { super("Invalid email or password"); }
    }

    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public static class InvalidTokenException extends GovPayException {
        public InvalidTokenException(String reason) { super("Invalid token: " + reason); }
    }

    @ResponseStatus(HttpStatus.FORBIDDEN)
    public static class AccountDisabledException extends GovPayException {
        public AccountDisabledException() { super("Account is disabled. Please contact support."); }
    }
}