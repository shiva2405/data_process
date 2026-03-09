package com.frauddetection.service;

import com.frauddetection.dto.TransactionRequest;
import com.frauddetection.entity.Transaction;
import com.frauddetection.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class SparkProcessorService {

    private final TransactionRepository transactionRepository;
    private final FraudDetectionService fraudDetectionService;

    /**
     * Process a transaction received from Kafka.
     * This method logs the transaction, persists it to the database,
     * and triggers fraud detection analysis.
     */
    public void processTransaction(TransactionRequest request) {
        log.info("Spark processing transaction: {}", request.getTransactionId());
        
        // Log transaction details for processing
        logTransactionDetails(request);
        
        // Default to DEBIT if transaction type is not provided
        Transaction.TransactionType transactionType = request.getTransactionType() != null 
                ? request.getTransactionType() 
                : Transaction.TransactionType.DEBIT;
        
        // Persist transaction to database
        Transaction transaction = Transaction.builder()
                .transactionId(request.getTransactionId())
                .userId(request.getUserId())
                .amount(request.getAmount())
                .merchant(request.getMerchant())
                .location(request.getLocation())
                .deviceId(request.getDeviceId())
                .timestamp(request.getTimestamp())
                .transactionType(transactionType)
                .createdAt(LocalDateTime.now())
                .build();
        
        Transaction savedTransaction = transactionRepository.save(transaction);
        log.info("Transaction saved to database after Spark processing: {}", savedTransaction.getTransactionId());
        
        // Trigger fraud detection analysis
        fraudDetectionService.analyzeTransaction(request);
    }

    /**
     * Log transaction details for processing and auditing.
     * This is a placeholder for future fraud detection logic.
     */
    private void logTransactionDetails(TransactionRequest request) {
        log.info("=== Transaction Processing ===");
        log.info("Transaction ID: {}", request.getTransactionId());
        log.info("User ID: {}", request.getUserId());
        log.info("Amount: {}", request.getAmount());
        log.info("Merchant: {}", request.getMerchant());
        log.info("Location: {}", request.getLocation());
        log.info("Device ID: {}", request.getDeviceId());
        log.info("Timestamp: {}", request.getTimestamp());
        log.info("Transaction Type: {}", request.getTransactionType());
        log.info("==============================");
    }
}