package com.paygrid.controller;

import com.paygrid.dto.request.DepositRequest;
import com.paygrid.dto.request.TransferRequest;
import com.paygrid.dto.request.UpdateTransactionStatusRequest;
import com.paygrid.dto.request.WithdrawalRequest;
import com.paygrid.dto.response.TransactionResponse;
import com.paygrid.entity.Role;
import com.paygrid.entity.TransactionStatus;
import com.paygrid.entity.TransactionType;
import com.paygrid.security.CustomUserDetails;
import com.paygrid.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transactions")
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "Transactions", description = "Transaction creation, transfer, deposit, withdrawal, history, and status management")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping("/transfer")
    @Operation(summary = "Transfer money", description = "Transfers money to another account atomically with balance and idempotency validation")
    public ResponseEntity<TransactionResponse> transfer(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody TransferRequest request) {
        TransactionResponse response = transactionService.transfer(userDetails.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/deposit")
    @Operation(summary = "Deposit funds", description = "Deposits money into the authenticated user's account")
    public ResponseEntity<TransactionResponse> deposit(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody DepositRequest request) {
        TransactionResponse response = transactionService.deposit(userDetails.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/withdraw")
    @Operation(summary = "Withdraw funds", description = "Withdraws money from the authenticated user's account")
    public ResponseEntity<TransactionResponse> withdraw(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody WithdrawalRequest request) {
        TransactionResponse response = transactionService.withdraw(userDetails.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{referenceId}")
    @Operation(summary = "Get transaction by reference ID", description = "Retrieves details of a transaction. Users can only access their own transactions.")
    public ResponseEntity<TransactionResponse> getByReferenceId(
            @PathVariable String referenceId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        boolean isAdmin = userDetails.getRole() == Role.ROLE_ADMIN;
        TransactionResponse response = transactionService.getTransactionByReferenceId(
                referenceId,
                userDetails.getId(),
                isAdmin
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "Get transaction history", description = "Returns paginated and filtered transactions for the authenticated user")
    public ResponseEntity<Page<TransactionResponse>> getHistory(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {

        Sort.Direction sortDirection = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));

        Page<TransactionResponse> response = transactionService.getTransactionHistory(
                userDetails.getId(),
                status,
                type,
                pageable
        );
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{referenceId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update transaction status", description = "Updates transaction status (ADMIN only)")
    public ResponseEntity<TransactionResponse> updateStatus(
            @PathVariable String referenceId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody UpdateTransactionStatusRequest request) {
        boolean isAdmin = userDetails.getRole() == Role.ROLE_ADMIN;
        TransactionResponse response = transactionService.updateTransactionStatus(referenceId, request, isAdmin);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get all platform transactions", description = "Returns all transactions with pagination and filters (ADMIN only)")
    public ResponseEntity<Page<TransactionResponse>> getAllTransactions(
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {

        Sort.Direction sortDirection = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));

        Page<TransactionResponse> response = transactionService.getAllTransactions(status, type, pageable);
        return ResponseEntity.ok(response);
    }
}

