package com.frauddetection.service;

import com.frauddetection.dto.TransactionRequest;
import com.frauddetection.dto.TransactionResponse;
import com.frauddetection.entity.Transaction;
import com.frauddetection.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;

    @Transactional
    public TransactionResponse createTransaction(TransactionRequest request) {
        log.info("Processing transaction: {}", request.getTransactionId());

        if (transactionRepository.existsByTransactionId(request.getTransactionId())) {
            throw new IllegalArgumentException("Transaction with ID " + request.getTransactionId() + " already exists");
        }

        // Default to DEBIT if transaction type is not provided
        Transaction.TransactionType transactionType = request.getTransactionType() != null 
                ? request.getTransactionType() 
                : Transaction.TransactionType.DEBIT;

        Transaction transaction = Transaction.builder()
                .transactionId(request.getTransactionId())
                .userId(request.getUserId())
                .amount(request.getAmount())
                .merchant(request.getMerchant())
                .location(request.getLocation())
                .deviceId(request.getDeviceId())
                .timestamp(request.getTimestamp())
                .transactionType(transactionType)
                .build();

        Transaction savedTransaction = transactionRepository.save(transaction);
        log.info("Transaction saved successfully: {}", savedTransaction.getTransactionId());

        return TransactionResponse.fromEntity(savedTransaction);
    }

    @Transactional(readOnly = true)
    public Optional<TransactionResponse> getTransactionById(String transactionId) {
        return transactionRepository.findByTransactionId(transactionId)
                .map(TransactionResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> getTransactionsByUserId(String userId) {
        return transactionRepository.findByUserIdOrderByTimestampDesc(userId)
                .stream()
                .map(TransactionResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> getAllTransactions() {
        return transactionRepository.findAll()
                .stream()
                .map(TransactionResponse::fromEntity)
                .collect(Collectors.toList());
    }
}
