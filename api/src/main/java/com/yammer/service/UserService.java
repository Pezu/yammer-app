package com.yammer.service;

import com.yammer.dto.UserRequest;
import com.yammer.dto.UserResponse;
import com.yammer.entity.LocationEntity;
import com.yammer.entity.UserEntity;
import com.yammer.repository.ClientRepository;
import com.yammer.repository.LocationRepository;
import com.yammer.repository.UserRepository;
import com.yammer.security.CurrentUserProvider;
import com.yammer.security.PasswordHasher;
import com.yammer.security.UserPrincipal;
import com.yammer.util.Strings;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final String SUPER = UserPrincipal.SUPER;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final ClientRepository clientRepository;
    private final LocationRepository locationRepository;
    private final CurrentUserProvider currentUser;
    private final PasswordHasher passwordHasher;
    private final QrCodeService qrCodeService;

    /** Web app base URL — the QR encodes {@code <base-url>/qr/<token>}. */
    @Value("${app.base-url}")
    private String baseUrl;

    /**
     * SUPER sees all users; everyone else sees only users in their own client.
     * SUPER accounts are system accounts and are never listed.
     */
    public List<UserResponse> list() {
        UserPrincipal me = currentUser.require();
        List<UserEntity> users = me.isSuper()
                ? userRepository.findAll(Sort.by("username"))
                : me.clientId() == null
                        ? List.of()
                        : userRepository.findByClientIdOrderByUsername(me.clientId());
        return users.stream()
                .filter(u -> !u.getRoles().contains(SUPER))
                .map(UserResponse::from)
                .toList();
    }

    public UserResponse create(UserRequest request) {
        UserPrincipal me = currentUser.require();
        String username = Strings.trimToNull(request.username());
        if (username == null && Strings.trimToNull(request.name()) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A name or username is required");
        }
        // Username and password may be omitted (e.g. staff who sign in only by QR):
        // the username falls back to a random UUID, the password to a random secret.
        if (username == null) {
            username = UUID.randomUUID().toString();
        }
        String password = request.password();
        if (password == null || password.isBlank()) {
            password = generatedPassword();
        }
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw conflict(username);
        }
        List<String> roles = sanitizeRoles(me, request.roles());
        UserEntity entity = new UserEntity();
        entity.setUsername(username);
        entity.setPassword(passwordHasher.hash(password));
        applyProfile(entity, me, roles, request);
        return UserResponse.from(userRepository.save(entity));
    }

    /** Random 192-bit password for users created without one (they sign in via QR). */
    private String generatedPassword() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public UserResponse update(UUID id, UserRequest request) {
        UserPrincipal me = currentUser.require();
        UserEntity entity = userRepository.findById(id).orElseThrow(() -> notFound(id));
        requireEditableBy(me, entity);
        String username = Strings.trimToNull(request.username());
        if (username == null) {
            username = entity.getUsername(); // blank = keep current
        } else if (!username.equalsIgnoreCase(entity.getUsername())
                && userRepository.existsByUsernameIgnoreCase(username)) {
            throw conflict(username);
        }
        List<String> roles = sanitizeRoles(me, request.roles());
        entity.setUsername(username);
        if (request.password() != null && !request.password().isBlank()) {
            entity.setPassword(passwordHasher.hash(request.password()));
        }
        applyProfile(entity, me, roles, request);
        return UserResponse.from(userRepository.save(entity));
    }

    public void delete(UUID id) {
        UserPrincipal me = currentUser.require();
        UserEntity entity = userRepository.findById(id).orElseThrow(() -> notFound(id));
        requireEditableBy(me, entity);
        userRepository.delete(entity);
    }

    /** A user's QR-login code: the login URL and its QR rendered as a base64 PNG. */
    public record QrLoginCode(String url, String png) {
    }

    /** The user's QR-login code; the secret is generated (and kept) on first request. */
    public QrLoginCode loginQr(UUID id) {
        UserPrincipal me = currentUser.require();
        UserEntity entity = userRepository.findById(id).orElseThrow(() -> notFound(id));
        requireEditableBy(me, entity);
        if (entity.getQrToken() == null) {
            entity.setQrToken(UUID.randomUUID());
            entity = userRepository.save(entity);
        }
        return qrCodeFor(entity);
    }

    /** Replace the user's QR secret with a fresh UUID — previously issued QR codes stop working. */
    public QrLoginCode resetQr(UUID id) {
        UserPrincipal me = currentUser.require();
        UserEntity entity = userRepository.findById(id).orElseThrow(() -> notFound(id));
        requireEditableBy(me, entity);
        entity.setQrToken(UUID.randomUUID());
        entity = userRepository.save(entity);
        return qrCodeFor(entity);
    }

    private QrLoginCode qrCodeFor(UserEntity entity) {
        String url = baseUrl.replaceAll("/+$", "") + "/login/qr/" + entity.getQrToken();
        return new QrLoginCode(url, Base64.getEncoder().encodeToString(qrCodeService.png(url, 320)));
    }

    /** A non-SUPER operator may only touch users inside their own client, and never SUPER users. */
    private void requireEditableBy(UserPrincipal me, UserEntity entity) {
        if (!me.isSuper()
                && (!Objects.equals(entity.getClientId(), me.clientId()) || entity.getRoles().contains(SUPER))) {
            throw notFound(entity.getId());
        }
    }

    private void applyProfile(UserEntity entity, UserPrincipal me, List<String> roles, UserRequest request) {
        entity.setName(Strings.trimToNull(request.name()));
        entity.setPhone(Strings.trimToNull(request.phone()));
        entity.setEmail(Strings.trimToNull(request.email()));
        entity.setRoles(roles);
        UUID clientId = resolveClient(me, roles, request.clientId());
        entity.setClientId(clientId);
        entity.setLocationId(resolveLocation(request.locationId(), clientId));
    }

    /**
     * Every client-scoped user must have a home location, one of their client's;
     * SUPER users (no client) carry none.
     */
    private UUID resolveLocation(UUID requested, UUID clientId) {
        if (clientId == null) {
            return null;
        }
        if (requested == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A location is required");
        }
        LocationEntity location = locationRepository.findById(requested)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown location: " + requested));
        if (!Objects.equals(location.getClientId(), clientId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Location does not belong to the user's client");
        }
        return requested;
    }

    /** A non-SUPER operator cannot grant the SUPER role. */
    private List<String> sanitizeRoles(UserPrincipal me, List<String> requested) {
        List<String> roles = requested == null ? new ArrayList<>() : new ArrayList<>(requested);
        if (!me.isSuper() && roles.contains(SUPER)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only SUPER users can grant the SUPER role");
        }
        return roles;
    }

    /**
     * SUPER users have no client. For every other user a client is required:
     * a SUPER operator may pick any existing client; a non-SUPER operator is forced
     * to their own client (the requested value is ignored).
     */
    private UUID resolveClient(UserPrincipal me, List<String> roles, UUID requested) {
        if (roles.contains(SUPER)) {
            return null;
        }
        if (!me.isSuper()) {
            if (me.clientId() == null) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not linked to a client");
            }
            return me.clientId();
        }
        if (requested == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A client is required for non-SUPER users");
        }
        if (!clientRepository.existsById(requested)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown client: " + requested);
        }
        return requested;
    }

    private ResponseStatusException notFound(UUID id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + id);
    }

    private ResponseStatusException conflict(String username) {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists: " + username);
    }
}
