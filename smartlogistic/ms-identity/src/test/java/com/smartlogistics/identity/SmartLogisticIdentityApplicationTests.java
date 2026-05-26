package com.smartlogistics.identity;

import com.smartlogistics.identity.dto.LoginRequest;
import com.smartlogistics.identity.dto.RegisterRequest;
import com.smartlogistics.identity.model.User;
import com.smartlogistics.identity.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "jwt.secret=test-secret-key-for-unit-tests-minimum-length",
    "jwt.expiration-ms=86400000"
})
class SmartLogisticIdentityApplicationTests {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void contextLoads() {
    }

    @Test
    void registerUser_ShouldReturn201AndToken() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("testuser");
        request.setPassword("password123");
        request.setRole("OPERATOR");

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/auth/register", request, Map.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody().get("token"));
        assertEquals("testuser", response.getBody().get("username"));
    }

    @Test
    void registerDuplicateUser_ShouldReturn409() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("dupuser");
        request.setPassword("password123");
        request.setRole("OPERATOR");

        restTemplate.postForEntity("/api/auth/register", request, Map.class);
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/auth/register", request, Map.class);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    }

    @Test
    void loginWithValidCredentials_ShouldReturn200AndToken() {
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setUsername("logintest");
        registerRequest.setPassword("password123");
        registerRequest.setRole("OPERATOR");
        restTemplate.postForEntity("/api/auth/register", registerRequest, Map.class);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("logintest");
        loginRequest.setPassword("password123");

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/auth/login", loginRequest, Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody().get("token"));
    }

    @Test
    void loginWithInvalidPassword_ShouldReturn401() {
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setUsername("logintest2");
        registerRequest.setPassword("password123");
        registerRequest.setRole("OPERATOR");
        restTemplate.postForEntity("/api/auth/register", registerRequest, Map.class);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("logintest2");
        loginRequest.setPassword("wrongpassword");

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/auth/login", loginRequest, Map.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void validateValidToken_ShouldReturn200() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("validateuser");
        request.setPassword("password123");
        request.setRole("OPERATOR");

        ResponseEntity<Map> registerResponse = restTemplate.postForEntity(
                "/api/auth/register", request, Map.class);
        String token = (String) registerResponse.getBody().get("token");

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/auth/validate", HttpMethod.POST, entity, Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("validateuser", response.getBody().get("username"));
    }

    @Test
    void validateInvalidToken_ShouldReturn401() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer invalid-token-here");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/auth/validate", HttpMethod.POST, entity, Map.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }
}
