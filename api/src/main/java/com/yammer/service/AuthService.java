package com.yammer.service;

import com.yammer.dto.LoginRequest;
import com.yammer.dto.LoginResponse;
import com.yammer.entity.UserEntity;
import com.yammer.repository.UserRepository;
import com.yammer.security.PasswordHasher;
import com.yammer.security.UserPrincipal;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthService {

    /**
     * The web app currently serves only the back office, so login is restricted to
     * ADMIN and SUPER operators.
     */
    private static final Set<String> ALLOWED_LOGIN_ROLES = Set.of("ADMIN", UserPrincipal.SUPER);

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordHasher passwordHasher;

    public LoginResponse login(LoginRequest request) {
        UserEntity user = userRepository
                .findByUsername(request.username())
                .orElseThrow(this::invalidCredentials);

        if (!passwordHasher.matches(request.password(), user.getPassword())) {
            throw invalidCredentials();
        }
        // Same 401 as bad credentials so a probe can't tell a valid password
        // from a disallowed role.
        if (user.getRoles().stream().noneMatch(ALLOWED_LOGIN_ROLES::contains)) {
            throw invalidCredentials();
        }
        // Transparently upgrade legacy MD5 hashes to BCrypt on a successful login.
        if (passwordHasher.needsUpgrade(user.getPassword())) {
            user.setPassword(passwordHasher.hash(request.password()));
            userRepository.save(user);
        }

        String token = jwtService.generateToken(user.getUsername(), user.getRoles(), user.getClientId(), user.getLocationId());
        return new LoginResponse(token, user.getUsername(), user.getName(), user.getRoles(), user.getClientId(), user.getLocationId());
    }

    /**
     * Sign in with a QR-login token (the secret embedded in a user's QR code).
     * The QR is a direct credential handed out by an operator, so unlike the
     * password form it is not restricted to ADMIN/SUPER roles.
     */
    public LoginResponse qrLogin(String qrToken) {
        UUID parsed;
        try {
            parsed = UUID.fromString(qrToken);
        } catch (IllegalArgumentException e) {
            throw invalidQrCode();
        }
        UserEntity user = userRepository.findByQrToken(parsed).orElseThrow(this::invalidQrCode);
        String token = jwtService.generateToken(user.getUsername(), user.getRoles(), user.getClientId(), user.getLocationId());
        return new LoginResponse(token, user.getUsername(), user.getName(), user.getRoles(), user.getClientId(), user.getLocationId());
    }

    private ResponseStatusException invalidQrCode() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid QR code");
    }

    private ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
    }
}
