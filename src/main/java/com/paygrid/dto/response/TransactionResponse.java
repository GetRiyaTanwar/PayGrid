package com.paygrid.dto.response;

import com.paygrid.entity.Transaction;
import com.paygrid.entity.TransactionStatus;
import com.paygrid.entity.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class TransactionResponse {

    private Long id;
    private String referenceId;
    private String idempotencyKey;
    private TransactionType type;
    private BigDecimal amount;
    private BigDecimal fee;
    private String currency;
    private TransactionStatus status;
    private String sourceAccountNumber;
    private String sourceUsername;
    private String targetAccountNumber;
    private String targetUsername;
    private String description;
    private String failureReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public TransactionResponse() {
    }

    public static TransactionResponse fromEntity(Transaction tx) {
        if (tx == null) {
            return null;
        }

        TransactionResponse resp = new TransactionResponse();
        resp.setId(tx.getId());
        resp.setReferenceId(tx.getReferenceId());
        resp.setIdempotencyKey(tx.getIdempotencyKey());
        resp.setType(tx.getType());
        resp.setAmount(tx.getAmount());
        resp.setFee(tx.getFee());
        resp.setCurrency(tx.getCurrency());
        resp.setStatus(tx.getStatus());

        if (tx.getSourceUser() != null) {
            resp.setSourceAccountNumber(tx.getSourceUser().getAccountNumber());
            resp.setSourceUsername(tx.getSourceUser().getUsername());
        }

        if (tx.getTargetUser() != null) {
            resp.setTargetAccountNumber(tx.getTargetUser().getAccountNumber());
            resp.setTargetUsername(tx.getTargetUser().getUsername());
        }

        resp.setDescription(tx.getDescription());
        resp.setFailureReason(tx.getFailureReason());
        resp.setCreatedAt(tx.getCreatedAt());
        resp.setUpdatedAt(tx.getUpdatedAt());

        return resp;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public TransactionType getType() {
        return type;
    }

    public void setType(TransactionType type) {
        this.type = type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getFee() {
        return fee;
    }

    public void setFee(BigDecimal fee) {
        this.fee = fee;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public void setStatus(TransactionStatus status) {
        this.status = status;
    }

    public String getSourceAccountNumber() {
        return sourceAccountNumber;
    }

    public void setSourceAccountNumber(String sourceAccountNumber) {
        this.sourceAccountNumber = sourceAccountNumber;
    }

    public String getSourceUsername() {
        return sourceUsername;
    }

    public void setSourceUsername(String sourceUsername) {
        this.sourceUsername = sourceUsername;
    }

    public String getTargetAccountNumber() {
        return targetAccountNumber;
    }

    public void setTargetAccountNumber(String targetAccountNumber) {
        this.targetAccountNumber = targetAccountNumber;
    }

    public String getTargetUsername() {
        return targetUsername;
    }

    public void setTargetUsername(String targetUsername) {
        this.targetUsername = targetUsername;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}

