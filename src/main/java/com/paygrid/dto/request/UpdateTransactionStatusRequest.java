package com.paygrid.dto.request;

import com.paygrid.entity.TransactionStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class UpdateTransactionStatusRequest {

    @NotNull(message = "Transaction status is required")
    private TransactionStatus status;

    @Size(max = 255, message = "Failure reason cannot exceed 255 characters")
    private String failureReason;

    public UpdateTransactionStatusRequest() {
    }

    public UpdateTransactionStatusRequest(TransactionStatus status, String failureReason) {
        this.status = status;
        this.failureReason = failureReason;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public void setStatus(TransactionStatus status) {
        this.status = status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }
}

