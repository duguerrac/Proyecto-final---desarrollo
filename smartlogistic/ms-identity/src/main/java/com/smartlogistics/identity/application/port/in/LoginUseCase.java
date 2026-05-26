package com.smartlogistics.identity.application.port.in;

public interface LoginUseCase {
    RegisterResult login(String username, String password);
}
