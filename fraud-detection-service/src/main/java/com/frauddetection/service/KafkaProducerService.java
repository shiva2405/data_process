package com.frauddetection.service;

import com.frauddetection.dto.TransactionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaProducerService {

    private final KafkaTemplate<String, TransactionRequest> kafkaTemplate;

    @Value("${spring.kafka.topic.transactions:fraud-transactions-topic}")
    private String transactionsTopic;

    public void sendTransaction(TransactionRequest transaction) {
        log.info("Sending transaction to Kafka topic: {}, transactionId: {}", 
                transactionsTopic, transaction.getTransactionId());
        
        kafkaTemplate.send(transactionsTopic, transaction.getTransactionId(), transaction)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send transaction to Kafka: {}", ex.getMessage());
                    } else {
                        log.info("Transaction sent successfully to partition {} with offset {}", 
                                result.getRecordMetadata().partition(), 
                                result.getRecordMetadata().offset());
                    }
                });
    }

    public void sendTransactionsBatch(List<TransactionRequest> transactions) {
        log.info("Sending batch of {} transactions to Kafka topic: {}", 
                transactions.size(), transactionsTopic);
        
        for (TransactionRequest transaction : transactions) {
            sendTransaction(transaction);
        }
        
        log.info("Batch of transactions sent to Kafka");
    }
}