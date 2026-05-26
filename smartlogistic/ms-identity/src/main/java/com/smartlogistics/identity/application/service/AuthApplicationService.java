package com.smartlogistics.identity.application.service;

import com.smartlogistics.identity.application.port.in.LoginUseCase;
import com.smartlogistics.identity.application.port.in.RegisterResult;
import com.smartlogistics.identity.application.port.in.RegisterUseCase;
import com.smartlogistics.identity.application.port.in.ValidateTokenUseCase;
import com.smartlogistics.identity.application.port.out.TokenServicePort;
import com.smartlogistics.identity.application.port.out.UserRepositoryPort;
import com.smartlogistics.identity.domain.exception.UserAlreadyExistsException;
import com.smartlogistics.identity.domain.model.Role;
import com.smartlogistics.identity.domain.model.User;

import java.util.UUID;

public class AuthApplicationService implements RegisterUseCase, LoginUseCase, ValidateTokenUseCase {

    private final UserRepositoryPort userRepository;
    private final TokenServicePort tokenService;
    private final PasswordEncoder passwordEncoder;

    public AuthApplicationService(UserRepositoryPort userRepository, TokenServicePort tokenService, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.tokenService = tokenService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public RegisterResult register(String username, String password, String role) {
        if (userRepository.existsByUsername(username)) {
            throw new UserAlreadyExistsException(username);
        }

        String hashedPassword = passwordEncoder.encode(password);
        Role userRole = (role != null && !role.isBlank()) ? Role.valueOf(role.toUpperCase()) : Role.OPERATOR;
        String id = UUID.randomUUID().toString();

        User user = User.create(id, username, hashedPassword, userRole);
        userRepository.save(user);

        String token = tokenService.generate(user.getUsername(), user.getRole().name());
        return new RegisterResult(token, user.getUsername(), user.getRole().name());
    }

    @Override
    public RegisterResult login(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Invalid username or password"));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid username or password");
        }

        String token = tokenService.generate(user.getUsername(), user.getRole().name());
        return new RegisterResult(token, user.getUsername(), user.getRole().name());
    }

    @Override
    public String validate(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String bearerPrefix = "Bearer ";
        if (token.startsWith(bearerPrefix)) {
            token = token.substring(bearerPrefix.length());
        }
        return tokenService.validate(token);
    }
}
