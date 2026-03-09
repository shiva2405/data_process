# Fraud Detection System Architecture

## Overview

The Fraud Detection Service is a Spring Boot-based backend system designed to process financial transactions, detect potential fraud patterns, and generate alerts. The system uses Apache Kafka for message queuing and Apache Spark for distributed processing.

## System Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              Client Applications                              │
│                    (Web, Mobile, External Systems)                          │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         Spring Boot REST API Layer                           │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐          │
│  │ Transaction      │  │ Bulk Upload      │  │ Query           │          │
│  │ Controller       │  │ Controller        │  │ Endpoints        │          │
│  │ (POST /api/v1/   │  │ (POST /api/v1/   │  │ (GET endpoints)  │          │
│  │  transactions)   │  │  bulk/upload)     │  │                  │          │
│  └────────┬─────────┘  └────────┬─────────┘  └────────┬─────────┘          │
└───────────┼─────────────────────┼─────────────────────┼─────────────────────┘
            │                     │                     │
            ▼                     ▼                     │
┌─────────────────────────────────────────────────────────────────────────────┐
│                              Service Layer                                   │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐          │
│  │ Kafka Producer   │  │ Bulk Upload       │  │ Transaction      │          │
│  │ Service          │  │ Service           │  │ Service          │          │
│  │                  │  │ (CSV Parsing)     │  │ (Direct DB)      │          │
│  └────────┬─────────┘  └────────┬─────────┘  └────────┬─────────┘          │
└───────────┼─────────────────────┼─────────────────────┼─────────────────────┘
            │                     │                     │
            ▼                     ▼                     │
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Apache Kafka                                       │
│                    Topic: fraud-transactions-topic                           │
│                    (Partitioned, Durable Message Queue)                      │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                        Kafka Consumer Service                                │
│                    (Listens for transaction messages)                        │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                        Spark Processor Service                               │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │  Transaction Processing Pipeline:                                      │   │
│  │  1. Receive transaction from Kafka                                    │   │
│  │  2. Log transaction details                                          │   │
│  │  3. Process through Spark (future: fraud detection rules)              │   │
│  │  4. Persist to database                                               │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                            H2 Database                                       │
│                    (In-Memory, Transaction Storage)                          │
│  ┌──────────────────┐                                                        │
│  │ transactions     │                                                        │
│  │ - transaction_id │                                                        │
│  │ - user_id        │                                                        │
│  │ - amount         │                                                        │
│  │ - merchant       │                                                        │
│  │ - location       │                                                        │
│  │ - device_id      │                                                        │
│  │ - timestamp      │                                                        │
│  │ - created_at     │                                                        │
│  └──────────────────┘                                                        │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Data Flow

### 1. Synchronous Transaction Creation (Direct to DB)

```
Client → REST API → TransactionService → Database
```

Use case: When immediate persistence is required without Kafka processing.

### 2. Asynchronous Transaction Creation (Via Kafka)

```
Client → REST API → KafkaProducerService → Kafka Topic
                                          ↓
                              KafkaConsumerService
                                          ↓
                              SparkProcessorService
                                          ↓
                                    Database
```

Use case: High-volume transaction processing with eventual consistency.

### 3. Bulk Upload Flow

```
CSV File → REST API → BulkUploadService → KafkaProducerService
                                                    ↓
                                             Kafka Topic
                                                    ↓
                                        KafkaConsumerService
                                                    ↓
                                        SparkProcessorService
                                                    ↓
                                              Database
```

Use case: Batch processing of historical transactions or migrations.

## Components

### 1. REST API Layer

| Controller | Purpose | Endpoints |
|------------|---------|-----------|
| TransactionController | Handle transaction operations | POST /api/v1/transactions, GET endpoints |
| BulkUploadController | Handle CSV file uploads | POST /api/v1/bulk/upload |

### 2. Service Layer

| Service | Responsibility |
|---------|----------------|
| TransactionService | Direct database operations for transactions |
| KafkaProducerService | Publish transactions to Kafka topics |
| KafkaConsumerService | Consume transactions from Kafka |
| SparkProcessorService | Process transactions through Spark pipeline |
| BulkUploadService | Parse CSV files and validate transactions |

### 3. Data Layer

| Component | Technology | Purpose |
|-----------|------------|---------|
| TransactionRepository | Spring Data JPA | Database access |
| H2 Database | In-memory | Transaction storage |

### 4. Message Queue

| Component | Configuration | Purpose |
|-----------|---------------|---------|
| Kafka Topic | fraud-transactions-topic (3 partitions) | Buffer transactions for processing |

### 5. Processing Engine

| Component | Configuration | Purpose |
|-----------|---------------|---------|
| Apache Spark | spark://localhost:7077 | Distributed transaction processing |

## Transaction Processing Pipeline

### Step 1: Transaction Ingestion

Transactions can be ingested through:
- **REST API**: Single transaction submission
- **Bulk Upload**: CSV file processing
- **Future**: Kafka streaming from external sources

### Step 2: Message Queuing

Transactions are published to Kafka with:
- **Key**: Transaction ID (ensures ordering per transaction)
- **Value**: Full transaction JSON payload
- **Partitioning**: By transaction ID hash

### Step 3: Spark Processing

Current processing includes:
1. Transaction logging for audit
2. Data validation
3. Persistence to database

Future enhancements:
1. Fraud detection rules engine
2. Pattern analysis
3. Anomaly detection
4. Real-time alerting

### Step 4: Persistence

Transactions are stored in H2 database with:
- Full transaction details
- Processing timestamp
- Audit trail

## Configuration

### Application Properties

```yaml
server:
  port: 8080

spring:
  kafka:
    bootstrap-servers: localhost:9092
    topic:
      transactions: fraud-transactions-topic

spark:
  master: spark://localhost:7077
  app-name: FraudDetectionSparkApp
```

### Kafka Configuration

| Property | Value | Purpose |
|----------|-------|---------|
| bootstrap-servers | localhost:9092 | Kafka broker address |
| key-serializer | StringSerializer | Transaction ID as key |
| value-serializer | JsonSerializer | Transaction object as JSON |
| acks | all | Ensure message delivery |
| retries | 3 | Retry on failure |

### Spark Configuration

| Property | Value | Purpose |
|----------|-------|---------|
| master | spark://localhost:7077 | Spark master URL |
| app-name | FraudDetectionSparkApp | Application identifier |

## Scalability

### Horizontal Scaling

1. **Multiple Kafka Consumers**: Scale consumer group instances
2. **Spark Workers**: Add more Spark worker nodes
3. **Database**: Replace H2 with distributed database (PostgreSQL, MySQL)

### Performance Considerations

1. **Kafka Partitioning**: 3 partitions allow parallel processing
2. **Async Processing**: Non-blocking transaction acceptance
3. **Batch Processing**: Bulk upload for high-volume scenarios

## Security Considerations

1. **Input Validation**: All inputs validated at API layer
2. **Error Handling**: Graceful error responses without exposing internals
3. **Audit Trail**: All transactions logged with timestamps

## Monitoring

### Health Endpoints

- `/actuator/health` - Application health status
- `/actuator/info` - Application information

### Logging

- Transaction processing logged at INFO level
- Errors logged at ERROR level with stack traces
- Structured logging for log aggregation

## Future Enhancements

1. **Fraud Detection Rules Engine**
   - Velocity checks (transaction frequency)
   - Amount thresholds
   - Geographic anomaly detection
   - Device fingerprinting

2. **Machine Learning Integration**
   - Anomaly detection models
   - User behavior profiling
   - Risk scoring

3. **Real-time Alerting**
   - Webhook notifications
   - Email/SMS alerts
   - Dashboard integration

4. **Database Migration**
   - PostgreSQL for production
   - Read replicas for query scaling
   - Data archival strategy

## Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| Spring Boot | 3.2.0 | Application framework |
| Spring Kafka | (managed) | Kafka integration |
| Apache Spark | 3.5.0 | Distributed processing |
| H2 Database | (managed) | In-memory database |
| SpringDoc OpenAPI | 2.3.0 | API documentation |
| Apache Commons CSV | 1.10.0 | CSV file parsing |
| Lombok | (managed) | Boilerplate reduction |