package com.reconengine.auth;

import com.reconengine.common.AppException;
import com.reconengine.user.User;
import com.reconengine.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CurrentActor currentActor;
    private final LoginAttemptLimiter attemptLimiter;
    /** Compared against when the username is unknown, so a bad username costs the same time as a bad password. */
    private final String decoyHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService,
                       CurrentActor currentActor, LoginAttemptLimiter attemptLimiter) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.currentActor = currentActor;
        this.attemptLimiter = attemptLimiter;
        this.decoyHash = passwordEncoder.encode("decoy-password-never-matches");
    }

    @Transactional(readOnly = true)
    public AuthController.LoginResponse login(String username, String password) {
        attemptLimiter.checkAllowed(username);

        Optional<User> found = users.findByUsername(username).filter(User::isEnabled);
        boolean passwordOk = passwordEncoder.matches(
                password, found.map(User::getPasswordHash).orElse(decoyHash));

        if (found.isEmpty() || !passwordOk) {
            attemptLimiter.recordFailure(username);
            throw new InvalidCredentialsException();
        }

        attemptLimiter.recordSuccess(username);
        User user = found.get();
        JwtService.IssuedToken token = jwtService.issue(user);

        return new AuthController.LoginResponse(
                token.value(),
                "Bearer",
                token.expiresAt(),
                user.getUsername(),
                user.getDisplayName(),
                user.getRole().name(),
                List.copyOf(user.getRole().permissions()));
    }

    @Transactional(readOnly = true)
    public AuthController.MeResponse describeCurrent() {
        CurrentActor.Actor actor = currentActor.require();
        User user = users.findById(actor.id())
                .orElseThrow(() -> new InvalidCredentialsException());

        return new AuthController.MeResponse(
                user.getId().toString(),
                user.getUsername(),
                user.getDisplayName(),
                user.getRole().name(),
                List.copyOf(user.getRole().permissions()));
    }

    /** Deliberately does not distinguish unknown user from wrong password. */
    public static class InvalidCredentialsException extends AppException {
        public InvalidCredentialsException() {
            super(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid username or password.");
        }
    }
}
