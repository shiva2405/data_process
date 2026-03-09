package com.frauddetection.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "alerts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "alert_id")
    private String alertId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "alert_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private AlertType alertType;

    @Column(name = "severity", nullable = false)
    @Enumerated(EnumType.STRING)
    private Severity severity;

    @Column(name = "message", nullable = false, length = 1000)
    private String message;

    @Column(name = "triggered_transaction_id")
    private String triggeredTransactionId;

    @Column(name = "amount", precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "details", length = 2000)
    private String details;

    @Column(name = "is_resolved")
    @Builder.Default
    private Boolean isResolved = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public enum AlertType {
        DAILY_DEBIT_LIMIT_EXCEEDED,
        RAPID_DEBIT_TRANSACTIONS,
        SUSPICIOUS_PATTERN,
        HIGH_VALUE_TRANSACTION
    }

    public enum Severity {
        LOW, MEDIUM, HIGH, CRITICAL
    }
}