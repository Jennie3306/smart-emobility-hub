package com.hcmut.emobility.shared.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Dang nhap va dang xuat do Spring Security xu ly:
 *   POST /api/auth/login   (form-urlencoded: username, password)  -> 200 hoac 401
 *   POST /api/auth/logout                                         -> 200
 * Controller nay chi tra ve thong tin nguoi dang dang nhap.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AppUserRepository repository;

    public AuthController(AppUserRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }
        return repository.findByUsername(authentication.getName())
                .<ResponseEntity<Map<String, Object>>>map(user -> ResponseEntity.ok(Map.of(
                        "username", user.getUsername(),
                        "fullName", user.getFullName(),
                        "role", user.getRole().name())))
                .orElseGet(() -> ResponseEntity.status(401).build());
    }
}
