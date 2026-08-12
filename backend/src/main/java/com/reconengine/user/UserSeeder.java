package com.reconengine.user;

import com.reconengine.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Creates the demo finance users if they are absent. Idempotent, so restarting the service
 * never rewrites a password an operator has changed.
 */
@Component
@ConditionalOnProperty(prefix = "app.seed", name = "users", havingValue = "true", matchIfMissing = false)
public class UserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UserSeeder.class);

    private static final List<SeedUser> SEED_USERS = List.of(
            new SeedUser("analyst", "Dana Okafor", Role.FINANCE_ANALYST),
            new SeedUser("approver", "Priya Raman", Role.FINANCE_APPROVER),
            new SeedUser("admin", "Ops Admin", Role.ADMIN));

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties properties;

    public UserSeeder(UserRepository users, PasswordEncoder passwordEncoder, AppProperties properties) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String hash = passwordEncoder.encode(properties.seed().defaultPassword());

        for (SeedUser seed : SEED_USERS) {
            if (users.existsByUsername(seed.username())) {
                continue;
            }
            users.save(new User(seed.username(), hash, seed.displayName(), seed.role()));
            log.info("seeded finance user '{}' with role {}", seed.username(), seed.role());
        }
    }

    private record SeedUser(String username, String displayName, Role role) {
    }
}
