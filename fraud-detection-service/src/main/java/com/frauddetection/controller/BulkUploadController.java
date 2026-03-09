package com.frauddetection.controller;

import com.frauddetection.service.BulkUploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/bulk")
@RequiredArgsConstructor
@Tag(name = "Bulk Upload API", description = "APIs for bulk uploading transactions via CSV files")
public class BulkUploadController {

    private final BulkUploadService bulkUploadService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload CSV file with transactions", 
              description = "Upload a CSV file containing transaction data for bulk processing. " +
                            "Transactions will be sent to Kafka for processing.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "File processed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid file format or content")
    })
    public ResponseEntity<Map<String, Object>> uploadTransactions(
            @RequestParam("file") MultipartFile file) {
        
        if (file.isEmpty()) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "File is empty");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        if (!file.getOriginalFilename().endsWith(".csv")) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Only CSV files are supported");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        BulkUploadService.BulkUploadResult result = bulkUploadService.processCsvFile(file);

        Map<String, Object> response = new HashMap<>();
        response.put("totalRecords", result.totalRecords());
        response.put("processedCount", result.processedCount());
        response.put("discardedCount", result.getDiscardedCount());
        response.put("errors", result.errors());
        response.put("discardedRecords", result.discardedRecords());
        response.put("hasErrors", result.hasErrors());
        response.put("hasDiscardedRecords", result.hasDiscardedRecords());
        response.put("message", "File processed. Valid transactions sent to Kafka for processing.");

        return ResponseEntity.ok(response);
    }
}