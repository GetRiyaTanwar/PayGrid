package com.paygrid.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paygrid.dto.request.DepositRequest;
import com.paygrid.dto.request.LoginRequest;
import com.paygrid.dto.request.RegisterRequest;
import com.paygrid.dto.request.TransferRequest;
import com.paygrid.dto.request.WithdrawalRequest;
import com.paygrid.dto.response.UserResponse;
import com.paygrid.entity.Role;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransactionControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String tokenA;
    private UserResponse userA;
    private String tokenB;
    private UserResponse userB;

    @BeforeEach
    void setUp() throws Exception {
        String uid = UUID.randomUUID().toString().substring(0, 6);

        // Register User A
        RegisterRequest reqA = new RegisterRequest(
                "user_a_" + uid,
                "usera_" + uid + "@example.com",
                "Password123!",
                Role.ROLE_USER,
                new BigDecimal("500.00")
        );
        MvcResult regResA = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqA)))
                .andExpect(status().isCreated())
                .andReturn();
        userA = objectMapper.readValue(regResA.getResponse().getContentAsString(), UserResponse.class);

        // Login User A
        MvcResult loginResA = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(reqA.getUsername(), "Password123!"))))
                .andExpect(status().isOk())
                .andReturn();
        tokenA = objectMapper.readTree(loginResA.getResponse().getContentAsString()).get("token").asText();

        // Register User B
        RegisterRequest reqB = new RegisterRequest(
                "user_b_" + uid,
                "userb_" + uid + "@example.com",
                "Password123!",
                Role.ROLE_USER,
                new BigDecimal("100.00")
        );
        MvcResult regResB = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqB)))
                .andExpect(status().isCreated())
                .andReturn();
        userB = objectMapper.readValue(regResB.getResponse().getContentAsString(), UserResponse.class);

        // Login User B
        MvcResult loginResB = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(reqB.getUsername(), "Password123!"))))
                .andExpect(status().isOk())
                .andReturn();
        tokenB = objectMapper.readTree(loginResB.getResponse().getContentAsString()).get("token").asText();
    }

    @Test
    @DisplayName("POST /api/v1/transactions/deposit - Deposit funds successfully")
    void deposit_Success() throws Exception {
        DepositRequest depositReq = new DepositRequest(new BigDecimal("150.00"), "Paycheck deposit", "dep-key-" + UUID.randomUUID());

        mockMvc.perform(post("/api/v1/transactions/deposit")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(depositReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type", is("DEPOSIT")))
                .andExpect(jsonPath("$.status", is("SUCCESS")))
                .andExpect(jsonPath("$.amount", is(150.00)))
                .andExpect(jsonPath("$.targetAccountNumber", is(userA.getAccountNumber())));
    }

    @Test
    @DisplayName("POST /api/v1/transactions/transfer - Transfer funds and verify idempotency")
    void transfer_WithIdempotency() throws Exception {
        String idemKey = "transfer-idem-" + UUID.randomUUID();
        TransferRequest transferReq = new TransferRequest(
                userB.getAccountNumber(),
                new BigDecimal("75.00"),
                "Gift payment",
                idemKey
        );

        // First transfer attempt
        MvcResult firstResult = mockMvc.perform(post("/api/v1/transactions/transfer")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transferReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type", is("TRANSFER")))
                .andExpect(jsonPath("$.status", is("SUCCESS")))
                .andExpect(jsonPath("$.amount", is(75.00)))
                .andExpect(jsonPath("$.referenceId", notNullValue()))
                .andReturn();

        String refId1 = objectMapper.readTree(firstResult.getResponse().getContentAsString()).get("referenceId").asText();

        // Second transfer attempt with identical idempotency key -> MUST return identical referenceId without re-debiting
        mockMvc.perform(post("/api/v1/transactions/transfer")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transferReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.referenceId", is(refId1)))
                .andExpect(jsonPath("$.amount", is(75.00)));

        // Verify userA balance: started 500, debited 75 once -> should be 425.00
        mockMvc.perform(get("/api/v1/users/profile")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance", is(425.00)));
    }

    @Test
    @DisplayName("POST /api/v1/transactions/withdraw - Withdraw funds successfully")
    void withdraw_Success() throws Exception {
        WithdrawalRequest withdrawReq = new WithdrawalRequest(
                new BigDecimal("50.00"),
                "Cash withdrawal",
                "with-key-" + UUID.randomUUID()
        );

        mockMvc.perform(post("/api/v1/transactions/withdraw")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(withdrawReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type", is("WITHDRAWAL")))
                .andExpect(jsonPath("$.status", is("SUCCESS")))
                .andExpect(jsonPath("$.amount", is(50.00)));
    }

    @Test
    @DisplayName("GET /api/v1/transactions - Retrieve paginated transaction history")
    void getTransactionHistory_Success() throws Exception {
        // Perform 1 deposit to create transaction record
        DepositRequest depositReq = new DepositRequest(new BigDecimal("20.00"), "Deposit 1", "dep-h-" + UUID.randomUUID());
        mockMvc.perform(post("/api/v1/transactions/deposit")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(depositReq)))
                .andExpect(status().isCreated());

        // Get history
        mockMvc.perform(get("/api/v1/transactions")
                        .header("Authorization", "Bearer " + tokenA)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", notNullValue()))
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("GET /api/v1/transactions/{referenceId} - Ownership check prevents other users from viewing transaction")
    void getTransaction_OwnershipEnforced() throws Exception {
        // Create deposit for user A
        DepositRequest depositReq = new DepositRequest(new BigDecimal("90.00"), "Private deposit", "dep-priv-" + UUID.randomUUID());
        MvcResult res = mockMvc.perform(post("/api/v1/transactions/deposit")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(depositReq)))
                .andExpect(status().isCreated())
                .andReturn();

        String refId = objectMapper.readTree(res.getResponse().getContentAsString()).get("referenceId").asText();

        // User A (owner) can view transaction
        mockMvc.perform(get("/api/v1/transactions/" + refId)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.referenceId", is(refId)));

        // User B (neither source nor target) attempts to view -> 403 Forbidden
        mockMvc.perform(get("/api/v1/transactions/" + refId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)))
                .andExpect(jsonPath("$.message", containsString("You do not have permission")));
    }
}

