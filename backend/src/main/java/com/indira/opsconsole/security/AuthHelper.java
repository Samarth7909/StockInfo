package com.indira.opsconsole.security;

import com.indira.opsconsole.domain.entity.AppUser;
import com.indira.opsconsole.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Helper used by controllers to resolve the current authenticated AppUser entity.
 * Also provides scope-checking helpers used for client-level access control.
 */
@Component
@RequiredArgsConstructor
public class AuthHelper {

    private final AppUserRepository userRepo;

    /**
     * Returns the full AppUser entity for the current security context principal.
     * Throws 401 if not authenticated.
     */
    public AppUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        String username = auth.getName();
        return userRepo.findByUsername(username)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "User not found in database: " + username));
    }

    /**
     * Returns a masked client ID for SUPPORT role (replaces last 4 chars with ****).
     * Other roles see the real client ID.
     */
    public String maskClientId(String clientId, AppUser user) {
        if (clientId == null) return null;
        return switch (user.getRole()) {
            case SUPPORT -> clientId.length() > 4
                ? clientId.substring(0, clientId.length() - 4) + "****"
                : "****";
            default -> clientId;
        };
    }

    /**
     * Verifies the user has scope access to the given client.
     * OPS_LEAD and AUDITOR always have access.
     */
    public void requireClientAccess(String clientId, AppUser user) {
        if (user.getRole() == com.indira.opsconsole.domain.enums.UserRole.OPS_LEAD) return;
        if (user.getRole() == com.indira.opsconsole.domain.enums.UserRole.AUDITOR) return;
        if (!user.getAccessibleClientIds().contains(clientId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Access denied to client: " + clientId);
        }
    }
}
