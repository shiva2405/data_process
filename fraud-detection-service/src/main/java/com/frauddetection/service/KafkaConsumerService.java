package com.frauddetection.service;

import com.frauddetection.dto.TransactionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaConsumerService {

    private final SparkProcessorService sparkProcessorService;

    @KafkaListener(
            topics = "${spring.kafka.topic.transactions:fraud-transactions-topic}",
            groupId = "${spring.kafka.consumer.group-id:fraud-detection-group}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeTransaction(TransactionRequest transaction) {
        log.info("Received transaction from Kafka: {}", transaction.getTransactionId());
        
        try {
            // Process the transaction through Spark
            sparkProcessorService.processTransaction(transaction);
            log.info("Transaction processed successfully: {}", transaction.getTransactionId());
        } catch (Exception e) {
            log.error("Error processing transaction {}: {}", 
                    transaction.getTransactionId(), e.getMessage(), e);
        }
    }
}