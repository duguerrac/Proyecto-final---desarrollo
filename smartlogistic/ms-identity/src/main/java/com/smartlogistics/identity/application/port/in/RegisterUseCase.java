package com.smartlogistics.identity.application.port.in;

public interface RegisterUseCase {
    RegisterResult register(String username, String password, String role);
}
