package com.indira.opsconsole.controller;

import com.indira.opsconsole.domain.entity.AppUser;
import com.indira.opsconsole.repository.AppUserRepository;
import com.indira.opsconsole.security.JwtService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authManager;
    private final JwtService            jwtService;
    private final AppUserRepository     userRepo;

    /**
     * POST /api/auth/login
     * Returns a JWT token plus user metadata.
     * An incorrect password returns 401 — never leaks whether the username exists.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest body) {
        try {
            Authentication auth = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(body.getUsername(), body.getPassword()));

            AppUser user = userRepo.findByUsername(auth.getName())
                .orElseThrow(() -> new BadCredentialsException("User not found"));

            List<String> clientIds = new ArrayList<>(user.getAccessibleClientIds());
            String token = jwtService.generate(user.getUsername(), user.getRole().name(), clientIds);

            return ResponseEntity.ok(Map.of(
                "token",       token,
                "username",    user.getUsername(),
                "role",        user.getRole().name(),
                "fullName",    user.getFullName() != null ? user.getFullName() : "",
                "clientIds",   clientIds
            ));
        } catch (BadCredentialsException | InternalAuthenticationServiceException e) {
            return ResponseEntity.status(401)
                .body(Map.of("error", "Invalid credentials"));
        }
    }

    @Data
    public static class LoginRequest {
        @NotBlank private String username;
        @NotBlank private String password;
    }
}
