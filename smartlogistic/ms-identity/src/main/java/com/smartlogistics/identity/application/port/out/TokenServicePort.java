package com.smartlogistics.identity.application.port.out;

public interface TokenServicePort {
    String generate(String username, String role);
    String validate(String token);
}
