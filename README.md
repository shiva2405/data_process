# Fraud Detection Service

A Spring Boot-based fraud detection backend system that consumes transaction events from Kafka, analyzes user transaction patterns, detects suspicious behavior, and generates fraud alerts.

## Architecture

For detailed architecture documentation, see [architecture.md](architecture.md).

## Prerequisites

- Java 17+
- Maven 3.8+
- Kafka (running locally on port 9092)
- Apache Spark (running locally on port 7077)

## Starting Required Services

Before running the fraud detection service, ensure Kafka and Spark are running. Execute the following command:

```bash
/usr/local/bin/start-services.sh
```

This will start:
- **Kafka**: localhost:9092
- **Spark**: Running in local mode (`local[*]`)

> **Note**: Spark is configured to run in local mode using all available CPU cores. Spark Web UI is disabled to avoid servlet conflicts with Spring Boot.

## Running the Service

```bash
cd fraud-detection-service
./mvnw spring-boot:run
```

Or build and run:

```bash
./mvnw clean package
java -jar target/fraud-detection-service-1.0.0-SNAPSHOT.jar
```

## API Endpoints

The service runs on port **8080**.

### Transaction API

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/transactions` | Submit a new transaction (async via Kafka) |
| POST | `/api/v1/transactions/sync` | Create transaction synchronously (direct to DB) |
| GET | `/api/v1/transactions` | Get all transactions |
| GET | `/api/v1/transactions/{transactionId}` | Get transaction by ID |
| GET | `/api/v1/transactions/user/{userId}` | Get transactions by user ID |

### Alert API

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/alerts` | Get all fraud detection alerts |
| GET | `/api/v1/alerts/{alertId}` | Get alert by ID |
| GET | `/api/v1/alerts/user/{userId}` | Get all alerts for a user |
| GET | `/api/v1/alerts/user/{userId}/unresolved` | Get unresolved alerts for a user |
| PATCH | `/api/v1/alerts/{alertId}/resolve` | Mark an alert as resolved |

### Bulk Upload API

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/bulk/upload` | Upload CSV file with transactions |

### Documentation

- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **API Docs**: http://localhost:8080/api-docs

### Database Console

- **H2 Console**: http://localhost:8080/h2-console
  - JDBC URL: `jdbc:h2:mem:frauddb`
  - Username: `sa`
  - Password: (empty)

## Transaction Model

```json
{
  "transactionId": "string",
  "userId": "string",
  "amount": 100.00,
  "merchant": "string",
  "location": "string (optional)",
  "deviceId": "string (optional)",
  "timestamp": "2024-01-01T12:00:00",
  "transactionType": "DEBIT | CREDIT"
}
```

## Alert Model

```json
{
  "alertId": "string",
  "userId": "string",
  "alertType": "DAILY_DEBIT_LIMIT_EXCEEDED | RAPID_DEBIT_TRANSACTIONS | SUSPICIOUS_PATTERN | HIGH_VALUE_TRANSACTION",
  "severity": "LOW | MEDIUM | HIGH | CRITICAL",
  "message": "string",
  "triggeredTransactionId": "string",
  "amount": 100.00,
  "details": "string",
  "isResolved": false,
  "createdAt": "2024-01-01T12:00:00"
}
```

## Fraud Detection Rules

The system automatically detects and creates alerts for the following patterns:

1. **Daily Debit Limit Exceeded**: When a user's total debit transactions exceed $100,000 in a single day
2. **Rapid Debit Transactions**: When a user makes 3 or more debit transactions within 5 minutes

## Bulk Upload CSV Format

The CSV file must have the following columns (header row required):

```csv
transactionId,userId,amount,merchant,location,deviceId,timestamp
tx-001,user-123,100.50,Amazon,New York,device-001,2024-01-15T10:30:00
tx-002,user-456,250.00,Best Buy,Los Angeles,device-002,2024-01-15T11:00:00
```

A sample CSV file is provided at [sample-transactions.csv](sample-transactions.csv).

### CSV Validation Rules

The bulk upload service validates each transaction against the following rules:
- **Required fields**: `transactionId`, `userId`, `amount`, `merchant`, `timestamp`
- **Amount**: Must be positive and not exceed $1,000,000
- **ID lengths**: Maximum 100 characters for `transactionId`, `userId`, `deviceId`
- **String lengths**: Maximum 255 characters for `merchant`, `location`
- **Invalid records**: Logged and discarded without failing the entire batch

## Technology Stack

- **Language**: Java 17
- **Framework**: Spring Boot 3.2.0
- **Database**: H2 (in-memory)
- **Message Queue**: Apache Kafka
- **Processing**: Apache Spark
- **API Documentation**: SpringDoc OpenAPI (Swagger)
- **Build Tool**: Maven

## Project Structure

```
fraud-detection-service/
├── src/main/java/com/frauddetection/
│   ├── FraudDetectionApplication.java
│   ├── config/
│   │   ├── GlobalExceptionHandler.java
│   │   ├── KafkaConfig.java
│   │   ├── OpenApiConfig.java
│   │   └── SparkConfig.java
│   ├── controller/
│   │   ├── AlertController.java
│   │   ├── BulkUploadController.java
│   │   └── TransactionController.java
│   ├── dto/
│   │   ├── TransactionRequest.java
│   │   └── TransactionResponse.java
│   ├── entity/
│   │   ├── Alert.java
│   │   └── Transaction.java
│   ├── repository/
│   │   ├── AlertRepository.java
│   │   └── TransactionRepository.java
│   └── service/
│       ├── BulkUploadService.java
│       ├── FraudDetectionService.java
│       ├── KafkaConsumerService.java
│       ├── KafkaProducerService.java
│       ├── SparkProcessorService.java
│       └── TransactionService.java
└── src/main/resources/
    └── application.yml
```

## Configuration

Key configuration properties in `application.yml`:

```yaml
server:
  port: 8080

spring:
  kafka:
    bootstrap-servers: localhost:9092
  datasource:
    url: jdbc:h2:mem:frauddb

spark:
  master: local[*]
  app-name: FraudDetectionSparkApp
```

## Error Handling

The service provides graceful error handling for:
- Missing API endpoints (returns 404 with helpful message)
- Validation errors (returns 400 with field details)
- Duplicate transactions (returns 409 Conflict)
- General server errors (returns 500)

## RCA: Spark Startup Failure (sun.nio.ch.DirectBuffer)

**Issue**: Application failed to start with `BeanCreationException` for `javaSparkContext`, caused by `StorageUtils` attempting to access `sun.nio.ch.DirectBuffer`.

**Root Cause**: Spark uses internal JDK classes that are not exported by default in Java 17 modules. The JVM blocks access to `sun.nio.ch` from unnamed modules, causing Spark initialization to fail.

**Fix**: Added JVM module export flags to Maven plugin configuration:
```
--add-exports=java.base/sun.nio.ch=ALL-UNNAMED
--add-exports=java.base/sun.util.calendar=ALL-UNNAMED
--add-opens=java.base/java.lang=ALL-UNNAMED
--add-opens=java.base/java.lang.invoke=ALL-UNNAMED
--add-opens=java.base/java.lang.reflect=ALL-UNNAMED
--add-opens=java.base/java.io=ALL-UNNAMED
--add-opens=java.base/java.net=ALL-UNNAMED
--add-opens=java.base/java.nio=ALL-UNNAMED
--add-opens=java.base/java.util=ALL-UNNAMED
--add-opens=java.base/java.util.concurrent=ALL-UNNAMED
--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED
--add-opens=java.base/sun.nio.ch=ALL-UNNAMED
--add-opens=java.base/sun.nio.cs=ALL-UNNAMED
--add-opens=java.base/sun.security.action=ALL-UNNAMED
--add-opens=java.base/sun.util.calendar=ALL-UNNAMED
```

This allows Spark to access the internal classes required for storage handling.

## RCA: Spark Web UI Servlet Conflict

**Issue**: `NoClassDefFoundError` for servlet classes when Spark Web UI is enabled alongside Spring Boot.

**Root Cause**: Spring Boot 3.x uses Jakarta EE (jakarta.servlet) while Spark Web UI uses Java EE (javax.servlet). This causes class loading conflicts.

**Fix**: 
1. Added javax.servlet-api dependency for Spark compatibility:
```xml
<dependency>
    <groupId>javax.servlet</groupId>
    <artifactId>javax.servlet-api</artifactId>
    <version>4.0.1</version>
    <scope>runtime</scope>
</dependency>
```
2. Disabled Spark Web UI in `SparkConfig.java`:
```java
.set("spark.ui.enabled", "false")
```
3. Changed Spark master to `local[*]` for local mode execution.

## Future Enhancements

- Fraud detection rules engine
- Pattern analysis for suspicious behavior
- Real-time alerting
- Machine learning-based anomaly detection
- User behavior profiling
