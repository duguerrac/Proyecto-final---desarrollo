package com.smartlogistics.identity.infrastructure.config;

import com.smartlogistics.identity.application.port.in.LoginUseCase;
import com.smartlogistics.identity.application.port.in.RegisterUseCase;
import com.smartlogistics.identity.application.port.in.ValidateTokenUseCase;
import com.smartlogistics.identity.application.port.out.TokenServicePort;
import com.smartlogistics.identity.application.port.out.UserRepositoryPort;
import com.smartlogistics.identity.application.service.AuthApplicationService;
import com.smartlogistics.identity.application.service.PasswordEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BeanConfig {

    @Bean
    public AuthApplicationService authApplicationService(
            UserRepositoryPort userRepository,
            TokenServicePort tokenService,
            PasswordEncoder passwordEncoder) {
        return new AuthApplicationService(userRepository, tokenService, passwordEncoder);
    }
}
