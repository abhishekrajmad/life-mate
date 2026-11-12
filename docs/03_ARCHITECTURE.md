# 03. ARCHITECTURE DESIGN

## 1. High-Level Microservice Architecture Diagram

```text
                  ┌─────────────────────────────────────────┐
                  │            React JS Frontend            │
                  └────────────────────┬────────────────────┘
                                       │ REST API (JSON)
                                       ▼
                         ┌──────────────────────────┐
                         │   Spring Cloud Gateway   │
                         └─────────────┬────────────┘
                                       │
            ┌──────────────────────────┴──────────────────────────┐
            ▼                                                     ▼
┌──────────────────────┐   HTTP / REST    ┌──────────────────────────────┐
│   Expense Service    │ ───────────────► │      AI Agent Service        │
│(Spring Boot + Postgres)◄─────────────── │   (Python / FastAPI + AI)    │
└───────────┬──────────┘                  └──────────────────────────────┘
            │                                             
            │ Async Event: ExpenseCreated
            ▼                                             
  ┌──────────────────┐                                   
  │  RabbitMQ Broker │ ◄────────────────── [Phase 2: Fitness Service]
  └─────────┬────────┘                     (Spring Boot + MongoDB)
            │                                             
            ▼                                             
┌──────────────────────┐                                 
│ Notification Service │                                 
│(Spring Boot Consumer)│                                 
└──────────────────────┘                                 
```

## 2. Component Breakdown & Service Responsibilities

### 1. API Gateway (`api-gateway`)
* **Framework:** Spring Cloud Gateway
* **Port:** `8080`
* **Responsibilities:** Central entry point, routes traffic to microservices, manages CORS policy, enforces Redis-based rate limiting.

### 2. Core Expense Service (`expense-service`)
* **Framework:** Java 17 / Spring Boot 3
* **Port:** `8081`
* **Responsibilities:** Manages financial ledgers, account balances, Flyway DB migrations (`db/migration`), REST communication with AI Agent Service, publishes `ExpenseCreatedEvent` to RabbitMQ.

### 3. AI Agent Service (`ai-agent-service`)
* **Framework:** Python 3.11 / FastAPI
* **Port:** `8000`
* **Responsibilities:** Prompts Google Gemini API using structured JSON schema output, parses raw natural text and SMS payloads into DTO objects.

### 4. Notification Service (`notification-service`)
* **Framework:** Java 17 / Spring Boot 3
* **Port:** `8082`
* **Responsibilities:** Listens to RabbitMQ queue (`budget.alerts.queue`), queries current monthly spending, and fires threshold warning alerts.

## 3. Data Flow Pipelines

### Flow A: Synchronous Transaction Ingestion & AI Parsing
1. React UI or Phone SMS Webhook sends payload to `http://localhost:8080/api/v1/expenses`.
2. `api-gateway` routes request to `expense-service`.
3. `expense-service` sends HTTP POST to `ai-agent-service` (`http://localhost:8000/api/v1/ai/parse-expense`).
4. `ai-agent-service` calls Gemini API, receives structured JSON, and returns DTO to `expense-service`.
5. `expense-service` saves transaction to PostgreSQL.

### Flow B: Asynchronous Budget Alerting Pipeline
1. Upon committing transaction to PostgreSQL, `expense-service` emits `ExpenseCreatedEvent` to RabbitMQ `expense.exchange`.
2. `expense-service` returns `201 Created` to client immediately.
3. `notification-service` consumes event from `budget.alerts.queue` asynchronously.
4. If monthly category sum $> \text{Category Budget Limit}$, an alert log/notification is generated.