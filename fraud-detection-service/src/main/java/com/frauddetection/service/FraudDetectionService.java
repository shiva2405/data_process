package com.frauddetection.service;

import com.frauddetection.dto.TransactionRequest;
import com.frauddetection.entity.Alert;
import com.frauddetection.entity.Transaction;
import com.frauddetection.repository.AlertRepository;
import com.frauddetection.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class FraudDetectionService {

    private final TransactionRepository transactionRepository;
    private final AlertRepository alertRepository;

    // Alert thresholds
    private static final BigDecimal DAILY_DEBIT_LIMIT = new BigDecimal("100000");
    private static final int RAPID_TRANSACTION_WINDOW_MINUTES = 5;
    private static final int RAPID_TRANSACTION_COUNT = 3;

    /**
     * Analyze transaction for fraud patterns and create alerts if needed.
     * This method checks for:
     * 1. Daily debit limit exceeded (> $100k in one day)
     * 2. Rapid debit transactions (3+ within 5 minutes)
     */
    @Transactional
    public void analyzeTransaction(TransactionRequest request) {
        log.info("Analyzing transaction {} for fraud patterns", request.getTransactionId());

        // Only analyze DEBIT transactions for these rules
        if (request.getTransactionType() != Transaction.TransactionType.DEBIT) {
            log.debug("Skipping fraud analysis for non-DEBIT transaction: {}", request.getTransactionId());
            return;
        }

        // Check daily debit limit
        checkDailyDebitLimit(request);

        // Check rapid transactions
        checkRapidTransactions(request);
    }

    private void checkDailyDebitLimit(TransactionRequest request) {
        String userId = request.getUserId();
        LocalDateTime startOfDay = request.getTimestamp().toLocalDate().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);

        // Get all debit transactions for the user today
        List<Transaction> dailyDebits = transactionRepository.findByUserId(userId).stream()
                .filter(t -> t.getTransactionType() == Transaction.TransactionType.DEBIT)
                .filter(t -> !t.getTimestamp().isBefore(startOfDay) && t.getTimestamp().isBefore(endOfDay))
                .toList();

        // Calculate total debit amount for today
        BigDecimal totalDailyDebit = dailyDebits.stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Add current transaction amount
        totalDailyDebit = totalDailyDebit.add(request.getAmount());

        // Check if limit exceeded
        if (totalDailyDebit.compareTo(DAILY_DEBIT_LIMIT) > 0) {
            log.warn("Daily debit limit exceeded for user {}: ${} (limit: ${})",
                    userId, totalDailyDebit, DAILY_DEBIT_LIMIT);

            // Check if alert already exists for this user today
            boolean alertExists = alertRepository.findByUserIdAndCreatedAtAfter(userId, startOfDay)
                    .stream()
                    .anyMatch(a -> a.getAlertType() == Alert.AlertType.DAILY_DEBIT_LIMIT_EXCEEDED);

            if (!alertExists) {
                Alert alert = Alert.builder()
                        .userId(userId)
                        .alertType(Alert.AlertType.DAILY_DEBIT_LIMIT_EXCEEDED)
                        .severity(Alert.Severity.HIGH)
                        .message(String.format("Daily debit limit exceeded: $%s (limit: $%s)",
                                totalDailyDebit, DAILY_DEBIT_LIMIT))
                        .triggeredTransactionId(request.getTransactionId())
                        .amount(request.getAmount())
                        .details(String.format("Total daily debits: $%s. Transaction count: %d",
                                totalDailyDebit, dailyDebits.size() + 1))
                        .build();

                alertRepository.save(alert);
                log.info("Created DAILY_DEBIT_LIMIT_EXCEEDED alert for user {}", userId);
            }
        }
    }

    private void checkRapidTransactions(TransactionRequest request) {
        String userId = request.getUserId();
        LocalDateTime windowStart = request.getTimestamp().minus(RAPID_TRANSACTION_WINDOW_MINUTES, ChronoUnit.MINUTES);
        LocalDateTime windowEnd = request.getTimestamp();

        // Get recent debit transactions within the time window
        List<Transaction> recentDebits = transactionRepository.findByUserId(userId).stream()
                .filter(t -> t.getTransactionType() == Transaction.TransactionType.DEBIT)
                .filter(t -> !t.getTimestamp().isBefore(windowStart) && !t.getTimestamp().isAfter(windowEnd))
                .toList();

        // Check if we have 3 or more transactions in the window (including current)
        if (recentDebits.size() + 1 >= RAPID_TRANSACTION_COUNT) {
            log.warn("Rapid debit transactions detected for user {}: {} transactions within {} minutes",
                    userId, recentDebits.size() + 1, RAPID_TRANSACTION_WINDOW_MINUTES);

            // Check if similar alert already exists recently
            boolean alertExists = alertRepository.findByUserIdAndCreatedAtAfter(userId, windowStart)
                    .stream()
                    .anyMatch(a -> a.getAlertType() == Alert.AlertType.RAPID_DEBIT_TRANSACTIONS);

            if (!alertExists) {
                Alert alert = Alert.builder()
                        .userId(userId)
                        .alertType(Alert.AlertType.RAPID_DEBIT_TRANSACTIONS)
                        .severity(Alert.Severity.MEDIUM)
                        .message(String.format("%d debit transactions within %d minutes",
                                recentDebits.size() + 1, RAPID_TRANSACTION_WINDOW_MINUTES))
                        .triggeredTransactionId(request.getTransactionId())
                        .amount(request.getAmount())
                        .details(String.format("Transaction IDs: %s, %s",
                                recentDebits.stream().map(Transaction::getTransactionId).toList(),
                                request.getTransactionId()))
                        .build();

                alertRepository.save(alert);
                log.info("Created RAPID_DEBIT_TRANSACTIONS alert for user {}", userId);
            }
        }
    }
}