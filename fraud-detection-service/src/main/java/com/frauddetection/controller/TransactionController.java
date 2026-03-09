package com.frauddetection.controller;

import com.frauddetection.dto.TransactionRequest;
import com.frauddetection.dto.TransactionResponse;
import com.frauddetection.service.KafkaProducerService;
import com.frauddetection.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Tag(name = "Transaction API", description = "APIs for managing transactions")
public class TransactionController {

    private final TransactionService transactionService;
    private final KafkaProducerService kafkaProducerService;

    @PostMapping
    @Operation(summary = "Create a new transaction", description = "Submit a new transaction for fraud detection processing. The transaction will be sent to Kafka for processing.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Transaction accepted and sent to Kafka"),
            @ApiResponse(responseCode = "400", description = "Invalid transaction data")
    })
    public ResponseEntity<TransactionResponse> createTransaction(
            @Valid @RequestBody TransactionRequest request) {
        // Send transaction to Kafka for async processing
        kafkaProducerService.sendTransaction(request);
        
        // Return accepted response
        TransactionResponse response = TransactionResponse.builder()
                .transactionId(request.getTransactionId())
                .userId(request.getUserId())
                .amount(request.getAmount())
                .merchant(request.getMerchant())
                .location(request.getLocation())
                .deviceId(request.getDeviceId())
                .timestamp(request.getTimestamp())
                .transactionType(request.getTransactionType())
                .build();
        
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @PostMapping("/sync")
    @Operation(summary = "Create transaction synchronously", description = "Create a transaction and save directly to database (bypassing Kafka)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Transaction created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid transaction data"),
            @ApiResponse(responseCode = "409", description = "Transaction already exists")
    })
    public ResponseEntity<TransactionResponse> createTransactionSync(
            @Valid @RequestBody TransactionRequest request) {
        TransactionResponse response = transactionService.createTransaction(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{transactionId}")
    @Operation(summary = "Get transaction by ID", description = "Retrieve a specific transaction by its ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Transaction found"),
            @ApiResponse(responseCode = "404", description = "Transaction not found")
    })
    public ResponseEntity<TransactionResponse> getTransactionById(
            @Parameter(description = "Transaction ID") @PathVariable String transactionId) {
        return transactionService.getTransactionById(transactionId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get transactions by user ID", description = "Retrieve all transactions for a specific user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Transactions retrieved successfully")
    })
    public ResponseEntity<List<TransactionResponse>> getTransactionsByUserId(
            @Parameter(description = "User ID") @PathVariable String userId) {
        List<TransactionResponse> transactions = transactionService.getTransactionsByUserId(userId);
        return ResponseEntity.ok(transactions);
    }

    @GetMapping
    @Operation(summary = "Get all transactions", description = "Retrieve all transactions")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "All transactions retrieved successfully")
    })
    public ResponseEntity<List<TransactionResponse>> getAllTransactions() {
        List<TransactionResponse> transactions = transactionService.getAllTransactions();
        return ResponseEntity.ok(transactions);
    }
}
