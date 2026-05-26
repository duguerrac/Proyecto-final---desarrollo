package com.smartlogistics.identity.infrastructure.adapter.in.rest;

import com.smartlogistics.identity.application.port.in.LoginUseCase;
import com.smartlogistics.identity.application.port.in.RegisterResult;
import com.smartlogistics.identity.application.port.in.RegisterUseCase;
import com.smartlogistics.identity.application.port.in.ValidateTokenUseCase;
import com.smartlogistics.identity.domain.exception.UserAlreadyExistsException;
import com.smartlogistics.identity.infrastructure.adapter.in.rest.dto.AuthResponse;
import com.smartlogistics.identity.infrastructure.adapter.in.rest.dto.LoginRequest;
import com.smartlogistics.identity.infrastructure.adapter.in.rest.dto.RegisterRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final RegisterUseCase registerUseCase;
    private final LoginUseCase loginUseCase;
    private final ValidateTokenUseCase validateTokenUseCase;

    public AuthController(RegisterUseCase registerUseCase, LoginUseCase loginUseCase, ValidateTokenUseCase validateTokenUseCase) {
        this.registerUseCase = registerUseCase;
        this.loginUseCase = loginUseCase;
        this.validateTokenUseCase = validateTokenUseCase;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        try {
            RegisterResult result = registerUseCase.register(
                    request.getUsername(), request.getPassword(), request.getRole());
            AuthResponse response = new AuthResponse(result.getToken(), result.getUsername(), result.getRole());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (UserAlreadyExistsException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            RegisterResult result = loginUseCase.login(
                    request.getUsername(), request.getPassword());
            AuthResponse response = new AuthResponse(result.getToken(), result.getUsername(), result.getRole());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/validate")
    public ResponseEntity<?> validate(
            @RequestHeader("Authorization") String authorization,
            HttpServletResponse response) {
        String username = validateTokenUseCase.validate(authorization);
        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        response.setHeader("X-Auth-User", username);
        return ResponseEntity.ok(Map.of("username", username));
    }
}
