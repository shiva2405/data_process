package com.frauddetection.dto;

import com.frauddetection.entity.Transaction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponse {

    private String transactionId;
    private String userId;
    private BigDecimal amount;
    private String merchant;
    private String location;
    private String deviceId;
    private LocalDateTime timestamp;
    private Transaction.TransactionType transactionType;
    private LocalDateTime createdAt;

    public static TransactionResponse fromEntity(Transaction transaction) {
        return TransactionResponse.builder()
                .transactionId(transaction.getTransactionId())
                .userId(transaction.getUserId())
                .amount(transaction.getAmount())
                .merchant(transaction.getMerchant())
                .location(transaction.getLocation())
                .deviceId(transaction.getDeviceId())
                .timestamp(transaction.getTimestamp())
                .transactionType(transaction.getTransactionType())
                .createdAt(transaction.getCreatedAt())
                .build();
    }
}
