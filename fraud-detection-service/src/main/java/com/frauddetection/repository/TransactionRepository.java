package com.frauddetection.repository;

import com.frauddetection.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, String> {

    Optional<Transaction> findByTransactionId(String transactionId);

    List<Transaction> findByUserId(String userId);

    List<Transaction> findByUserIdOrderByTimestampDesc(String userId);

    List<Transaction> findByMerchant(String merchant);

    List<Transaction> findByDeviceId(String deviceId);

    boolean existsByTransactionId(String transactionId);
}
