package com.paygrid.service;

import com.paygrid.dto.request.DepositRequest;
import com.paygrid.dto.request.TransferRequest;
import com.paygrid.dto.request.UpdateTransactionStatusRequest;
import com.paygrid.dto.request.WithdrawalRequest;
import com.paygrid.dto.response.TransactionResponse;
import com.paygrid.entity.Role;
import com.paygrid.entity.Transaction;
import com.paygrid.entity.TransactionStatus;
import com.paygrid.entity.TransactionType;
import com.paygrid.entity.User;
import com.paygrid.exception.InsufficientBalanceException;
import com.paygrid.exception.InvalidTransactionException;
import com.paygrid.exception.UnauthorizedAccessException;
import com.paygrid.repository.TransactionRepository;
import com.paygrid.repository.UserRepository;
import com.paygrid.service.impl.TransactionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TransactionServiceImpl transactionService;

    private User sourceUser;
    private User targetUser;

    @BeforeEach
    void setUp() {
        sourceUser = new User(
                "alice",
                "alice@example.com",
                "hash1",
                Role.ROLE_USER,
                "PG-ALICE01",
                new BigDecimal("1000.00")
        );
        sourceUser.setId(10L);

        targetUser = new User(
                "bob",
                "bob@example.com",
                "hash2",
                Role.ROLE_USER,
                "PG-BOB002",
                new BigDecimal("200.00")
        );
        targetUser.setId(20L);
    }

    @Test
    @DisplayName("Should transfer funds atomically between source and target user")
    void transfer_Success() {
        TransferRequest request = new TransferRequest(
                "PG-BOB002",
                new BigDecimal("300.00"),
                "Payment for services",
                "idem-key-1"
        );

        when(transactionRepository.findByIdempotencyKey("idem-key-1")).thenReturn(Optional.empty());
        when(userRepository.findById(10L)).thenReturn(Optional.of(sourceUser));
        when(userRepository.findByAccountNumber("PG-BOB002")).thenReturn(Optional.of(targetUser));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionResponse response = transactionService.transfer(10L, request);

        assertThat(response).isNotNull();
        assertThat(response.getAmount()).isEqualByComparingTo(new BigDecimal("300.00"));
        assertThat(response.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(response.getType()).isEqualTo(TransactionType.TRANSFER);

        // Verify balances updated
        assertThat(sourceUser.getBalance()).isEqualByComparingTo(new BigDecimal("700.00"));
        assertThat(targetUser.getBalance()).isEqualByComparingTo(new BigDecimal("500.00"));

        verify(userRepository).save(sourceUser);
        verify(userRepository).save(targetUser);
        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    @DisplayName("Should throw InsufficientBalanceException when source balance is inadequate")
    void transfer_InsufficientBalance_ThrowsException() {
        TransferRequest request = new TransferRequest(
                "PG-BOB002",
                new BigDecimal("1500.00"),
                "Overdraft attempt",
                "idem-key-2"
        );

        when(transactionRepository.findByIdempotencyKey("idem-key-2")).thenReturn(Optional.empty());
        when(userRepository.findById(10L)).thenReturn(Optional.of(sourceUser));
        when(userRepository.findByAccountNumber("PG-BOB002")).thenReturn(Optional.of(targetUser));

        assertThatThrownBy(() -> transactionService.transfer(10L, request))
                .isInstanceOf(InsufficientBalanceException.class)
                .hasMessageContaining("Insufficient funds");

        verify(userRepository, never()).save(sourceUser);
        verify(userRepository, never()).save(targetUser);
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    @DisplayName("Should prevent self-transfer")
    void transfer_SelfTransfer_ThrowsException() {
        TransferRequest request = new TransferRequest(
                "PG-ALICE01",
                new BigDecimal("100.00"),
                "Self transfer attempt",
                null
        );

        when(userRepository.findById(10L)).thenReturn(Optional.of(sourceUser));
        when(userRepository.findByAccountNumber("PG-ALICE01")).thenReturn(Optional.of(sourceUser));

        assertThatThrownBy(() -> transactionService.transfer(10L, request))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("Cannot transfer funds to your own account");
    }

    @Test
    @DisplayName("Should return existing transaction on duplicate idempotency key without re-debiting")
    void transfer_Idempotency_ReturnsExisting() {
        TransferRequest request = new TransferRequest(
                "PG-BOB002",
                new BigDecimal("100.00"),
                "Duplicate key transfer",
                "existing-idem-key"
        );

        Transaction existingTx = new Transaction(
                "TXN-EXISTING-12345",
                "existing-idem-key",
                sourceUser,
                targetUser,
                TransactionType.TRANSFER,
                new BigDecimal("100.00"),
                BigDecimal.ZERO,
                "USD",
                TransactionStatus.SUCCESS,
                "Original transfer"
        );

        when(transactionRepository.findByIdempotencyKey("existing-idem-key")).thenReturn(Optional.of(existingTx));

        TransactionResponse response = transactionService.transfer(10L, request);

        assertThat(response).isNotNull();
        assertThat(response.getReferenceId()).isEqualTo("TXN-EXISTING-12345");

        // Verify balance was NOT touched again
        assertThat(sourceUser.getBalance()).isEqualByComparingTo(new BigDecimal("1000.00"));
        verify(userRepository, never()).save(sourceUser);
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    @DisplayName("Should successfully deposit funds into user account")
    void deposit_Success() {
        DepositRequest request = new DepositRequest(new BigDecimal("250.00"), "Direct deposit", "dep-1");

        when(transactionRepository.findByIdempotencyKey("dep-1")).thenReturn(Optional.empty());
        when(userRepository.findById(10L)).thenReturn(Optional.of(sourceUser));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));

        TransactionResponse response = transactionService.deposit(10L, request);

        assertThat(response).isNotNull();
        assertThat(response.getType()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(response.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(sourceUser.getBalance()).isEqualByComparingTo(new BigDecimal("1250.00"));

        verify(userRepository).save(sourceUser);
    }

    @Test
    @DisplayName("Should successfully withdraw funds from user account")
    void withdraw_Success() {
        WithdrawalRequest request = new WithdrawalRequest(new BigDecimal("400.00"), "ATM withdrawal", "with-1");

        when(transactionRepository.findByIdempotencyKey("with-1")).thenReturn(Optional.empty());
        when(userRepository.findById(10L)).thenReturn(Optional.of(sourceUser));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));

        TransactionResponse response = transactionService.withdraw(10L, request);

        assertThat(response).isNotNull();
        assertThat(response.getType()).isEqualTo(TransactionType.WITHDRAWAL);
        assertThat(sourceUser.getBalance()).isEqualByComparingTo(new BigDecimal("600.00"));

        verify(userRepository).save(sourceUser);
    }

    @Test
    @DisplayName("Should reject access when user is neither source nor target and not ADMIN")
    void getTransactionByReferenceId_Unauthorized_ThrowsException() {
        Transaction tx = new Transaction(
                "TXN-PRIVATE-999",
                null,
                sourceUser,
                targetUser,
                TransactionType.TRANSFER,
                new BigDecimal("100.00"),
                BigDecimal.ZERO,
                "USD",
                TransactionStatus.SUCCESS,
                "Secret transfer"
        );

        when(transactionRepository.findByReferenceId("TXN-PRIVATE-999")).thenReturn(Optional.of(tx));

        // User ID 999 is neither source (10) nor target (20)
        assertThatThrownBy(() -> transactionService.getTransactionByReferenceId("TXN-PRIVATE-999", 999L, false))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("You do not have permission to view this transaction");
    }

    @Test
    @DisplayName("Should allow ADMIN to update transaction status")
    void updateTransactionStatus_Admin_Success() {
        Transaction tx = new Transaction(
                "TXN-UPDATE-001",
                null,
                sourceUser,
                targetUser,
                TransactionType.TRANSFER,
                new BigDecimal("100.00"),
                BigDecimal.ZERO,
                "USD",
                TransactionStatus.PENDING,
                "Pending transaction"
        );

        when(transactionRepository.findByReferenceId("TXN-UPDATE-001")).thenReturn(Optional.of(tx));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));

        UpdateTransactionStatusRequest request = new UpdateTransactionStatusRequest(
                TransactionStatus.FAILED,
                "Flagged for compliance review"
        );

        TransactionResponse response = transactionService.updateTransactionStatus("TXN-UPDATE-001", request, true);

        assertThat(response.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(response.getFailureReason()).isEqualTo("Flagged for compliance review");
    }

    @Test
    @DisplayName("Should reject non-admin updating transaction status")
    void updateTransactionStatus_NonAdmin_ThrowsException() {
        UpdateTransactionStatusRequest request = new UpdateTransactionStatusRequest(
                TransactionStatus.SUCCESS,
                null
        );

        assertThatThrownBy(() -> transactionService.updateTransactionStatus("TXN-UPDATE-001", request, false))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("Only administrators are authorized");
    }
}

