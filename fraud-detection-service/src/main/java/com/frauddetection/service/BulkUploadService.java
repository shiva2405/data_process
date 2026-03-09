package com.frauddetection.service;

import com.frauddetection.dto.TransactionRequest;
import com.frauddetection.entity.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class BulkUploadService {

    private final KafkaProducerService kafkaProducerService;

    private static final DateTimeFormatter[] DATE_FORMATTERS = {
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
    };

    // Expected CSV headers
    private static final Set<String> EXPECTED_HEADERS = Set.of(
            "transactionid", "userid", "amount", "merchant", "location", "deviceid", "timestamp", "transactiontype"
    );

    // Maximum amount allowed (validation rule)
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000.00");

    // Maximum transaction ID length
    private static final int MAX_ID_LENGTH = 100;

    /**
     * Process a CSV file containing transactions and send them to Kafka.
     * Expected CSV format: transactionId,userId,amount,merchant,location,deviceId,timestamp,transactionType
     */
    public BulkUploadResult processCsvFile(MultipartFile file) {
        log.info("Processing CSV file: {}", file.getOriginalFilename());
        
        List<TransactionRequest> validTransactions = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<String> discardedRecords = new ArrayList<>();
        int lineNumber = 0;
        int totalRecords = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
             CSVParser csvParser = new CSVParser(reader, 
                     CSVFormat.DEFAULT.withFirstRecordAsHeader().withIgnoreHeaderCase().withTrim())) {

            // Validate headers
            Set<String> actualHeaders = csvParser.getHeaderMap().keySet().stream()
                    .map(String::toLowerCase)
                    .collect(java.util.stream.Collectors.toSet());
            
            if (!actualHeaders.containsAll(EXPECTED_HEADERS)) {
                Set<String> missingHeaders = new java.util.HashSet<>(EXPECTED_HEADERS);
                missingHeaders.removeAll(actualHeaders);
                log.error("Missing required headers: {}", missingHeaders);
                throw new IllegalArgumentException("Missing required CSV headers: " + missingHeaders);
            }

            for (CSVRecord record : csvParser) {
                lineNumber++;
                totalRecords++;
                try {
                    TransactionRequest transaction = parseTransaction(record, lineNumber);
                    if (transaction != null) {
                        // Validate against model constraints
                        ValidationResult validation = validateTransaction(transaction, lineNumber);
                        if (validation.isValid()) {
                            validTransactions.add(transaction);
                        } else {
                            discardedRecords.add(validation.getMessage());
                            log.warn("Discarding invalid transaction at line {}: {}", 
                                    lineNumber, validation.getMessage());
                        }
                    }
                } catch (Exception e) {
                    String error = String.format("Line %d: %s - Record discarded", lineNumber, e.getMessage());
                    errors.add(error);
                    discardedRecords.add(error);
                    log.warn("Error parsing line {}: {}", lineNumber, e.getMessage());
                }
            }

        } catch (IOException e) {
            log.error("Error reading CSV file: {}", e.getMessage());
            throw new RuntimeException("Failed to read CSV file: " + e.getMessage(), e);
        }

        // Send valid transactions to Kafka
        if (!validTransactions.isEmpty()) {
            kafkaProducerService.sendTransactionsBatch(validTransactions);
            log.info("Sent {} transactions to Kafka for processing", validTransactions.size());
        }

        log.info("CSV processing complete - Total: {}, Valid: {}, Discarded: {}", 
                totalRecords, validTransactions.size(), discardedRecords.size());

        return new BulkUploadResult(validTransactions.size(), errors, discardedRecords, totalRecords);
    }

    private TransactionRequest parseTransaction(CSVRecord record, int lineNumber) {
        try {
            String transactionId = record.get("transactionId");
            String userId = record.get("userId");
            String amountStr = record.get("amount");
            String merchant = record.get("merchant");
            String location = record.get("location");
            String deviceId = record.get("deviceId");
            String timestampStr = record.get("timestamp");
            String transactionTypeStr = record.get("transactionType");

            // Validate required fields
            if (transactionId == null || transactionId.isBlank()) {
                throw new IllegalArgumentException("transactionId is required");
            }
            if (userId == null || userId.isBlank()) {
                throw new IllegalArgumentException("userId is required");
            }
            if (amountStr == null || amountStr.isBlank()) {
                throw new IllegalArgumentException("amount is required");
            }
            if (merchant == null || merchant.isBlank()) {
                throw new IllegalArgumentException("merchant is required");
            }
            if (timestampStr == null || timestampStr.isBlank()) {
                throw new IllegalArgumentException("timestamp is required");
            }

            BigDecimal amount = new BigDecimal(amountStr);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("amount must be positive");
            }

            LocalDateTime timestamp = parseTimestamp(timestampStr);
            
            // Parse transaction type (default to DEBIT if not provided)
            Transaction.TransactionType transactionType = parseTransactionType(transactionTypeStr);

            return TransactionRequest.builder()
                    .transactionId(transactionId.trim())
                    .userId(userId.trim())
                    .amount(amount)
                    .merchant(merchant.trim())
                    .location(location != null ? location.trim() : null)
                    .deviceId(deviceId != null ? deviceId.trim() : null)
                    .timestamp(timestamp)
                    .transactionType(transactionType)
                    .build();

        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse transaction: " + e.getMessage(), e);
        }
    }

    private Transaction.TransactionType parseTransactionType(String transactionTypeStr) {
        if (transactionTypeStr == null || transactionTypeStr.isBlank()) {
            log.debug("Transaction type not provided, defaulting to DEBIT");
            return Transaction.TransactionType.DEBIT;
        }
        
        try {
            return Transaction.TransactionType.valueOf(transactionTypeStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid transaction type '{}', defaulting to DEBIT. Valid values: DEBIT, CREDIT", 
                    transactionTypeStr);
            return Transaction.TransactionType.DEBIT;
        }
    }

    /**
     * Validate transaction against model constraints.
     * Logs and discards invalid transactions.
     */
    private ValidationResult validateTransaction(TransactionRequest transaction, int lineNumber) {
        // Validate transaction ID length
        if (transaction.getTransactionId().length() > MAX_ID_LENGTH) {
            return ValidationResult.invalid(String.format(
                    "Line %d: transactionId exceeds maximum length of %d", 
                    lineNumber, MAX_ID_LENGTH));
        }

        // Validate user ID length
        if (transaction.getUserId().length() > MAX_ID_LENGTH) {
            return ValidationResult.invalid(String.format(
                    "Line %d: userId exceeds maximum length of %d", 
                    lineNumber, MAX_ID_LENGTH));
        }

        // Validate amount is within reasonable bounds
        if (transaction.getAmount().compareTo(MAX_AMOUNT) > 0) {
            return ValidationResult.invalid(String.format(
                    "Line %d: amount %s exceeds maximum allowed %s", 
                    lineNumber, transaction.getAmount(), MAX_AMOUNT));
        }

        // Validate merchant length
        if (transaction.getMerchant().length() > 255) {
            return ValidationResult.invalid(String.format(
                    "Line %d: merchant name exceeds maximum length of 255", lineNumber));
        }

        // Validate location length if present
        if (transaction.getLocation() != null && transaction.getLocation().length() > 255) {
            return ValidationResult.invalid(String.format(
                    "Line %d: location exceeds maximum length of 255", lineNumber));
        }

        // Validate deviceId length if present
        if (transaction.getDeviceId() != null && transaction.getDeviceId().length() > MAX_ID_LENGTH) {
            return ValidationResult.invalid(String.format(
                    "Line %d: deviceId exceeds maximum length of %d", 
                    lineNumber, MAX_ID_LENGTH));
        }

        // Validate timestamp is not in the future
        if (transaction.getTimestamp().isAfter(LocalDateTime.now())) {
            log.warn("Line {}: timestamp is in the future - allowing but flagging", lineNumber);
            // We allow future timestamps but log them as suspicious
        }

        return ValidationResult.valid();
    }

    private LocalDateTime parseTimestamp(String timestampStr) {
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDateTime.parse(timestampStr, formatter);
            } catch (Exception ignored) {
                // Try next formatter
            }
        }
        throw new IllegalArgumentException("Unable to parse timestamp: " + timestampStr);
    }

    /**
     * Validation result helper class.
     */
    private static class ValidationResult {
        private final boolean valid;
        private final String message;

        private ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }

        static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        static ValidationResult invalid(String message) {
            return new ValidationResult(false, message);
        }

        boolean isValid() {
            return valid;
        }

        String getMessage() {
            return message;
        }
    }

    /**
     * Result of bulk upload operation.
     */
    public record BulkUploadResult(
            int processedCount, 
            List<String> errors,
            List<String> discardedRecords,
            int totalRecords
    ) {
        public boolean hasErrors() {
            return !errors.isEmpty();
        }

        public boolean hasDiscardedRecords() {
            return !discardedRecords.isEmpty();
        }

        public int getDiscardedCount() {
            return discardedRecords.size();
        }
    }
}