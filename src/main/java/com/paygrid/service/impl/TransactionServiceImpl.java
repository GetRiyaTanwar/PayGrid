package com.paygrid.service.impl;

import com.paygrid.dto.request.DepositRequest;
import com.paygrid.dto.request.TransferRequest;
import com.paygrid.dto.request.UpdateTransactionStatusRequest;
import com.paygrid.dto.request.WithdrawalRequest;
import com.paygrid.dto.response.TransactionResponse;
import com.paygrid.entity.Transaction;
import com.paygrid.entity.TransactionStatus;
import com.paygrid.entity.TransactionType;
import com.paygrid.entity.User;
import com.paygrid.exception.InsufficientBalanceException;
import com.paygrid.exception.InvalidTransactionException;
import com.paygrid.exception.ResourceNotFoundException;
import com.paygrid.exception.UnauthorizedAccessException;
import com.paygrid.repository.TransactionRepository;
import com.paygrid.repository.UserRepository;
import com.paygrid.service.TransactionService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    public TransactionServiceImpl(TransactionRepository transactionRepository, UserRepository userRepository) {
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public TransactionResponse transfer(Long currentUserId, TransferRequest request) {
        // 1. Idempotency Check
        if (StringUtils.hasText(request.getIdempotencyKey())) {
            Optional<Transaction> existing = transactionRepository.findByIdempotencyKey(request.getIdempotencyKey().trim());
            if (existing.isPresent()) {
                return TransactionResponse.fromEntity(existing.get());
            }
        }

        User sourceUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Source user not found with id: " + currentUserId));

        User targetUser = userRepository.findByAccountNumber(request.getTargetAccountNumber())
                .orElseThrow(() -> new ResourceNotFoundException("Target account not found: " + request.getTargetAccountNumber()));

        // Prevent self-transfer
        if (sourceUser.getId().equals(targetUser.getId())) {
            throw new InvalidTransactionException("Cannot transfer funds to your own account");
        }

        BigDecimal amount = request.getAmount();
        BigDecimal fee = BigDecimal.ZERO; // Optional fee calculation
        BigDecimal totalDebit = amount.add(fee);

        if (sourceUser.getBalance().compareTo(totalDebit) < 0) {
            throw new InsufficientBalanceException(
                    "Insufficient funds. Current balance: " + sourceUser.getBalance() + ", requested: " + totalDebit
            );
        }

        // Atomically debit source and credit target
        sourceUser.setBalance(sourceUser.getBalance().subtract(totalDebit));
        targetUser.setBalance(targetUser.getBalance().add(amount));

        userRepository.save(sourceUser);
        userRepository.save(targetUser);

        String referenceId = generateReferenceId();
        String idempotencyKey = StringUtils.hasText(request.getIdempotencyKey()) ? request.getIdempotencyKey().trim() : null;

        Transaction transaction = new Transaction(
                referenceId,
                idempotencyKey,
                sourceUser,
                targetUser,
                TransactionType.TRANSFER,
                amount,
                fee,
                "USD",
                TransactionStatus.SUCCESS,
                request.getDescription()
        );

        Transaction savedTx = transactionRepository.save(transaction);
        return TransactionResponse.fromEntity(savedTx);
    }

    @Override
    @Transactional
    public TransactionResponse deposit(Long currentUserId, DepositRequest request) {
        // Idempotency check
        if (StringUtils.hasText(request.getIdempotencyKey())) {
            Optional<Transaction> existing = transactionRepository.findByIdempotencyKey(request.getIdempotencyKey().trim());
            if (existing.isPresent()) {
                return TransactionResponse.fromEntity(existing.get());
            }
        }

        User targetUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + currentUserId));

        BigDecimal amount = request.getAmount();
        targetUser.setBalance(targetUser.getBalance().add(amount));
        userRepository.save(targetUser);

        String referenceId = generateReferenceId();
        String idempotencyKey = StringUtils.hasText(request.getIdempotencyKey()) ? request.getIdempotencyKey().trim() : null;

        Transaction transaction = new Transaction(
                referenceId,
                idempotencyKey,
                null,
                targetUser,
                TransactionType.DEPOSIT,
                amount,
                BigDecimal.ZERO,
                "USD",
                TransactionStatus.SUCCESS,
                request.getDescription()
        );

        Transaction savedTx = transactionRepository.save(transaction);
        return TransactionResponse.fromEntity(savedTx);
    }

    @Override
    @Transactional
    public TransactionResponse withdraw(Long currentUserId, WithdrawalRequest request) {
        // Idempotency check
        if (StringUtils.hasText(request.getIdempotencyKey())) {
            Optional<Transaction> existing = transactionRepository.findByIdempotencyKey(request.getIdempotencyKey().trim());
            if (existing.isPresent()) {
                return TransactionResponse.fromEntity(existing.get());
            }
        }

        User sourceUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + currentUserId));

        BigDecimal amount = request.getAmount();
        if (sourceUser.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException(
                    "Insufficient funds. Current balance: " + sourceUser.getBalance() + ", withdrawal amount: " + amount
            );
        }

        sourceUser.setBalance(sourceUser.getBalance().subtract(amount));
        userRepository.save(sourceUser);

        String referenceId = generateReferenceId();
        String idempotencyKey = StringUtils.hasText(request.getIdempotencyKey()) ? request.getIdempotencyKey().trim() : null;

        Transaction transaction = new Transaction(
                referenceId,
                idempotencyKey,
                sourceUser,
                null,
                TransactionType.WITHDRAWAL,
                amount,
                BigDecimal.ZERO,
                "USD",
                TransactionStatus.SUCCESS,
                request.getDescription()
        );

        Transaction savedTx = transactionRepository.save(transaction);
        return TransactionResponse.fromEntity(savedTx);
    }

    @Override
    @Transactional(readOnly = true)
    public TransactionResponse getTransactionByReferenceId(String referenceId, Long currentUserId, boolean isAdmin) {
        Transaction tx = transactionRepository.findByReferenceId(referenceId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with reference: " + referenceId));

        if (!isAdmin) {
            boolean isSource = tx.getSourceUser() != null && tx.getSourceUser().getId().equals(currentUserId);
            boolean isTarget = tx.getTargetUser() != null && tx.getTargetUser().getId().equals(currentUserId);
            if (!isSource && !isTarget) {
                throw new UnauthorizedAccessException("You do not have permission to view this transaction");
            }
        }

        return TransactionResponse.fromEntity(tx);
    }

    @Override
    @Transactional
    public TransactionResponse updateTransactionStatus(
            String referenceId,
            UpdateTransactionStatusRequest request,
            boolean isAdmin) {
        if (!isAdmin) {
            throw new UnauthorizedAccessException("Only administrators are authorized to update transaction statuses");
        }

        Transaction tx = transactionRepository.findByReferenceId(referenceId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with reference: " + referenceId));

        tx.setStatus(request.getStatus());
        if (request.getFailureReason() != null) {
            tx.setFailureReason(request.getFailureReason());
        }

        Transaction updated = transactionRepository.save(tx);
        return TransactionResponse.fromEntity(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactionHistory(
            Long currentUserId,
            TransactionStatus status,
            TransactionType type,
            Pageable pageable) {

        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + currentUserId));

        Specification<Transaction> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // User must be either source or target
            Predicate isSource = cb.equal(root.get("sourceUser"), currentUser);
            Predicate isTarget = cb.equal(root.get("targetUser"), currentUser);
            predicates.add(cb.or(isSource, isTarget));

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return transactionRepository.findAll(spec, pageable).map(TransactionResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TransactionResponse> getAllTransactions(
            TransactionStatus status,
            TransactionType type,
            Pageable pageable) {

        Specification<Transaction> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return transactionRepository.findAll(spec, pageable).map(TransactionResponse::fromEntity);
    }

    private String generateReferenceId() {
        return "TXN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }
}

