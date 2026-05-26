package com.smartlogistics.identity.application.port.out;

import com.smartlogistics.identity.domain.model.User;

import java.util.Optional;

public interface UserRepositoryPort {
    User save(User user);
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
}
