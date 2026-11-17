package com.paygrid.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paygrid.dto.request.LoginRequest;
import com.paygrid.dto.request.RegisterRequest;
import com.paygrid.entity.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("POST /api/v1/auth/register - Should register user and return 201 Created")
    void register_Success() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "integration_user1",
                "integration1@example.com",
                "StrongPassword123!",
                Role.ROLE_USER,
                new BigDecimal("100.00")
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username", is("integration_user1")))
                .andExpect(jsonPath("$.email", is("integration1@example.com")))
                .andExpect(jsonPath("$.accountNumber", startsWith("PG-")))
                .andExpect(jsonPath("$.balance", is(100.00)));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register - Should return 400 Bad Request on invalid payload")
    void register_ValidationFailure() throws Exception {
        RegisterRequest invalidRequest = new RegisterRequest(
                "", // Blank username
                "not-an-email",
                "short", // Less than 8 characters
                null,
                new BigDecimal("-50.00") // Negative deposit
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.validationErrors.username", notNullValue()))
                .andExpect(jsonPath("$.validationErrors.email", notNullValue()))
                .andExpect(jsonPath("$.validationErrors.password", notNullValue()))
                .andExpect(jsonPath("$.validationErrors.initialDeposit", notNullValue()));
    }

    @Test
    @DisplayName("Full Auth Flow: Register -> Login -> Access Protected Endpoint")
    void fullAuthFlow_Success() throws Exception {
        // 1. Register
        RegisterRequest registerReq = new RegisterRequest(
                "flow_user",
                "flow@example.com",
                "Password999!",
                Role.ROLE_USER,
                new BigDecimal("50.00")
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated());

        // 2. Login
        LoginRequest loginReq = new LoginRequest("flow_user", "Password999!");

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.tokenType", is("Bearer")))
                .andExpect(jsonPath("$.user.username", is("flow_user")))
                .andReturn();

        String responseBody = loginResult.getResponse().getContentAsString();
        String token = objectMapper.readTree(responseBody).get("token").asText();

        // 3. Access /api/v1/auth/me without token -> 401 Unauthorized
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());

        // 4. Access /api/v1/auth/me with Bearer token -> 200 OK
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is("flow_user")))
                .andExpect(jsonPath("$.email", is("flow@example.com")));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login - Should return 401 Unauthorized on invalid password")
    void login_InvalidCredentials() throws Exception {
        LoginRequest loginReq = new LoginRequest("non_existent_user", "WrongPassword!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.message", containsString("Invalid username or password")));
    }
}

