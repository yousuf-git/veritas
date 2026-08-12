package com.reconengine.auth;

import com.reconengine.common.Errors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * The authenticated identity behind the current request. Every audited write stamps this,
 * so "who resolved this exception" is answered by the token, never by a client-supplied field.
 */
@Component
public class CurrentActor {

    public Actor require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new Errors.Forbidden("No authenticated actor on this request.");
        }
        return new Actor(UUID.fromString(jwt.getSubject()), jwt.getClaimAsString("username"));
    }

    public record Actor(UUID id, String username) {
    }
}
