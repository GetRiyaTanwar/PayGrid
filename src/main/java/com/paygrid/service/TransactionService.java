package com.paygrid.service;

import com.paygrid.dto.request.DepositRequest;
import com.paygrid.dto.request.TransferRequest;
import com.paygrid.dto.request.UpdateTransactionStatusRequest;
import com.paygrid.dto.request.WithdrawalRequest;
import com.paygrid.dto.response.TransactionResponse;
import com.paygrid.entity.TransactionStatus;
import com.paygrid.entity.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TransactionService {

    TransactionResponse transfer(Long currentUserId, TransferRequest request);

    TransactionResponse deposit(Long currentUserId, DepositRequest request);

    TransactionResponse withdraw(Long currentUserId, WithdrawalRequest request);

    TransactionResponse getTransactionByReferenceId(String referenceId, Long currentUserId, boolean isAdmin);

    TransactionResponse updateTransactionStatus(String referenceId, UpdateTransactionStatusRequest request, boolean isAdmin);

    Page<TransactionResponse> getTransactionHistory(
            Long currentUserId,
            TransactionStatus status,
            TransactionType type,
            Pageable pageable
    );

    Page<TransactionResponse> getAllTransactions(
            TransactionStatus status,
            TransactionType type,
            Pageable pageable
    );
}

