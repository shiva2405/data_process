package com.frauddetection.controller;

import com.frauddetection.entity.Alert;
import com.frauddetection.repository.AlertRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
@Tag(name = "Alert API", description = "APIs for managing fraud detection alerts")
public class AlertController {

    private final AlertRepository alertRepository;

    @GetMapping
    @Operation(summary = "Get all alerts", description = "Retrieve all fraud detection alerts")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Alerts retrieved successfully")
    })
    public ResponseEntity<List<Alert>> getAllAlerts() {
        List<Alert> alerts = alertRepository.findAll();
        return ResponseEntity.ok(alerts);
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get alerts by user ID", description = "Retrieve all fraud detection alerts for a specific user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Alerts retrieved successfully")
    })
    public ResponseEntity<List<Alert>> getAlertsByUserId(
            @Parameter(description = "User ID") @PathVariable String userId) {
        List<Alert> alerts = alertRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return ResponseEntity.ok(alerts);
    }

    @GetMapping("/user/{userId}/unresolved")
    @Operation(summary = "Get unresolved alerts by user ID", description = "Retrieve unresolved fraud detection alerts for a specific user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Alerts retrieved successfully")
    })
    public ResponseEntity<List<Alert>> getUnresolvedAlertsByUserId(
            @Parameter(description = "User ID") @PathVariable String userId) {
        List<Alert> alerts = alertRepository.findByUserIdAndIsResolved(userId, false);
        return ResponseEntity.ok(alerts);
    }

    @GetMapping("/{alertId}")
    @Operation(summary = "Get alert by ID", description = "Retrieve a specific alert by its ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Alert found"),
            @ApiResponse(responseCode = "404", description = "Alert not found")
    })
    public ResponseEntity<Alert> getAlertById(
            @Parameter(description = "Alert ID") @PathVariable String alertId) {
        return alertRepository.findById(alertId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{alertId}/resolve")
    @Operation(summary = "Resolve alert", description = "Mark an alert as resolved")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Alert resolved successfully"),
            @ApiResponse(responseCode = "404", description = "Alert not found")
    })
    public ResponseEntity<Alert> resolveAlert(
            @Parameter(description = "Alert ID") @PathVariable String alertId) {
        return alertRepository.findById(alertId)
                .map(alert -> {
                    alert.setIsResolved(true);
                    Alert resolvedAlert = alertRepository.save(alert);
                    return ResponseEntity.ok(resolvedAlert);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}