package com.frauddetection.repository;

import com.frauddetection.entity.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AlertRepository extends JpaRepository<Alert, String> {

    List<Alert> findByUserId(String userId);

    List<Alert> findByUserIdOrderByCreatedAtDesc(String userId);

    List<Alert> findByUserIdAndIsResolved(String userId, Boolean isResolved);

    List<Alert> findByAlertType(Alert.AlertType alertType);

    List<Alert> findBySeverity(Alert.Severity severity);

    List<Alert> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    List<Alert> findByUserIdAndCreatedAtAfter(String userId, LocalDateTime after);

    long countByUserIdAndIsResolved(String userId, Boolean isResolved);
}