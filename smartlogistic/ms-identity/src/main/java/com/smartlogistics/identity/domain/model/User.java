package com.smartlogistics.identity.domain.model;

public class User {

    private final String id;
    private final String username;
    private final String passwordHash;
    private final Role role;
    private final java.time.LocalDateTime createdAt;

    public User(String id, String username, String passwordHash, Role role, java.time.LocalDateTime createdAt) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.createdAt = createdAt;
    }

    public static User create(String id, String username, String passwordHash, Role role) {
        return new User(id, username, passwordHash, role, java.time.LocalDateTime.now());
    }

    public String getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public java.time.LocalDateTime getCreatedAt() { return createdAt; }
}
