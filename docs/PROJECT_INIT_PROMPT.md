# SYSTEM CONTEXT & DEVELOPER MASTER PROMPT: LIFE MATE

> **Instructions for the AI Coding Agent (Claude Code / Cursor / Codex / Antigravity):**
> Read this document completely before generating any code. This document defines the exact project context, microservice architecture, tech stack, directory structure, constraints, and daily implementation workflow for the **Life Mate** project.
> You must strictly follow all engineering rules and scope boundaries outlined below. Do not add out-of-scope features or alter the defined tech stack.

## 1. PROJECT OVERVIEW & GOALS

* **Project Title:** Life Mate – An AI-Driven Personal Operations Microservices Platform

* **Developer Name:** Abhishek Raj Madaram

* **Methodology:** TrainingMug AI Development Framework (ADF v1.0)

* **Target Delivery Window:** 2 Weeks (14 Days / \~34 Total Working Hours)

* **Core Problem Solved:** Reduces friction in personal finance logging by accepting unstructured natural language text (via manual UI entry or automated bank SMS webhooks), parsing it into structured JSON via an AI service (Gemini API), saving it to a relational ledger, and asynchronously checking budget limits over RabbitMQ without blocking user API responses.

## 2. SCOPE BOUNDARIES & CONSTRAINTS

### In-Scope (Phase 1 MVP - Must Build)

1. **Core Expense Service (Spring Boot):** Transaction ledger, Flyway DB migrations, account balances, monthly budget caps CRUD.

2. **AI Agent Service (Python / FastAPI):** Natural language entity extraction using `google-genai` SDK to convert raw text/SMS into structured JSON.

3. **Notification Service (Spring Boot):** Asynchronous RabbitMQ consumer that receives `ExpenseCreatedEvent` and checks for budget limit breaches.

4. **API Gateway (Spring Cloud Gateway):** Route aggregation, CORS, and Redis rate limiting.

5. **Automated SMS Webhook:** An endpoint (`POST /api/v1/webhook/sms`) in Expense Service triggered via phone automation apps (MacroDroid/Tasker) or cURL.

6. **Frontend (React.js SPA):** Dashboard with natural language input box, expense ledger, progress gauges for budget caps, and 4 UI states (Loading, Error, Empty, Data).

7. **Infrastructure (Docker Compose):** Single local setup running PostgreSQL, Redis, and RabbitMQ.

### Strictly Out-of-Scope (Do NOT Build in Phase 1)

* **Phase 2 Fitness Module (MongoDB):** Keep strictly in documentation (`03_ARCHITECTURE.md`) as a future architectural expansion. Do NOT write code or driver setup for MongoDB.

* **Razorpay / External Payment Gateways:** Deferred to future roadmap.

* **Complex Security Frameworks:** Simple JWT / Bearer header validation; do not build OAuth2 servers or complex IAM.

## 3. TECHNOLOGY MATRIX & SERVICE BOUNDARIES

| Service Name | Primary Tech Stack | Database / Broker | Key Responsibilities | 
| ----- | ----- | ----- | ----- | 
| **api-gateway** | Java 17 / Spring Cloud Gateway | Redis *(Rate Limiting)* | Central entry point, CORS, routing to backend services. | 
| **expense-service** | Java 17 / Spring Boot 3 | PostgreSQL 15 *(Flyway)* | Transactional ledger, accounts, categories, budgets, DB migrations, RabbitMQ publisher. | 
| **ai-agent-service** | Python 3.11 / FastAPI | Stateless *(Gemini API)* | Consumes raw text/SMS strings, queries Gemini API, returns strict structured JSON DTOs. | 
| **notification-service** | Java 17 / Spring Boot 3 | Memory / Log Sink | Listens to RabbitMQ queue (`budget.alerts.queue`) and evaluates budget threshold warnings. | 
| **frontend-web** | React.js 18 + Tailwind / CSS | N/A | SPA Dashboard, Natural Language Text Input, Expense Ledger Table, Budget Gauges. | 

## 4. SYSTEM DATA FLOW & ARCHITECTURE PIPELINE

```
                               ┌─────────────────────────┐
                               │  React SPA / Mobile SMS │
                               └────────────┬────────────┘
                                            │ REST (JSON)
                                            ▼
                               ┌─────────────────────────┐
                               │  Spring Cloud Gateway   │
                               └────────────┬────────────┘
                                            │
                                            ▼
┌───────────────────────┐   HTTP / REST    ┌─────────────────────────┐
│    Expense Service    │ ───────────────► │    AI Agent Service     │
│ (Spring Boot + Postgres)◄─────────────── │ (FastAPI + Gemini API)  │
└───────────┬───────────┘   Structured DTO └─────────────────────────┘
            │
            │ Async Event: ExpenseCreated
            ▼
  ┌───────────────────┐
  │  RabbitMQ Broker  │
  └─────────┬─────────┘
            │
            ▼
┌───────────────────────┐
│ Notification Service  │ ──► Checks category limit in Postgres/Redis
│ (Spring Boot Consumer)│     and logs/fires budget breach alert.
└───────────────────────┘

```

## 5. REPOSITORY DIRECTORY LAYOUT (ADF v1.0)

```
life-mate/
├── docs/                               # Mandatory Documentation Suite
│   ├── 01_PROJECT_CONTEXT.md
│   ├── 02_REQUIREMENTS.md
│   ├── 03_ARCHITECTURE.md
│   ├── 04_DATABASE.md
│   ├── 05_API_CONTRACT.md
│   ├── 06_UI_FLOW.md
│   ├── 07_TASKS.md
│   ├── 08_CODING_STANDARDS.md
│   ├── 09_TESTING.md
│   ├── 10_DEPLOYMENT.md
│   └── PROJECT_INIT_PROMPT.md          # Master AI context file
├── backend/
│   ├── api-gateway/                    # Spring Cloud Gateway
│   ├── expense-service/                # Core Ledger & Budget Service
│   ├── ai-agent-service/               # Python FastAPI NLP Service
│   └── notification-service/           # RabbitMQ Event Consumer
├── frontend/                           # React.js SPA
├── docker/                             # Dockerfiles & docker-compose.yml
├── postman/                            # API Test Collections
└── .github/                            # CI/CD Workflows

```

## 6. DATABASE SCHEMA & CONTRACT SPECS

### PostgreSQL Migration SQL (`V1__init_schema.sql`)

```
CREATE TABLE accounts (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(50) NOT NULL,
    account_name VARCHAR(100) NOT NULL,
    balance DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    currency VARCHAR(10) DEFAULT 'INR',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE categories (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    monthly_budget_limit DECIMAL(12, 2) DEFAULT 0.00
);

CREATE TABLE transactions (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT REFERENCES accounts(id),
    category_id BIGINT REFERENCES categories(id),
    amount DECIMAL(12, 2) NOT NULL,
    transaction_type VARCHAR(20) NOT NULL DEFAULT 'EXPENSE',
    merchant VARCHAR(100),
    raw_input TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

```

### Core API Contracts

#### 1. AI Parse Endpoint (`ai-agent-service`)

* **`POST /api/v1/ai/parse-expense`**

* **Request:** `{"raw_text": "Spent 450 rupees on lunch at Paradise Biryani", "currency": "INR"}`

* **Response (200 OK):**

  ```
  {
    "amount": 450.00,
    "currency": "INR",
    "category": "Food & Dining",
    "merchant": "Paradise Biryani",
    "confidence_score": 0.98
  }
  
  ```

#### 2. SMS Webhook Endpoint (`expense-service`)

* **`POST /api/v1/expenses/webhook/sms`**

* **Request:** `{"sender": "HDFCBK", "body": "Rs. 450.00 debited from A/c XX1234 on 11-Nov-26 at Swiggy."}`

#### 3. RabbitMQ Message Payload (`expense.exchange` $\rightarrow$ `expense.created`)

```
{
  "eventId": "evt_1001",
  "userId": "usr_abhishek",
  "amount": 450.00,
  "categoryId": 2,
  "categoryName": "Food & Dining",
  "timestamp": "2026-11-11T13:45:00Z"
}

```

## 7. MANDATORY CODING STANDARDS FOR AI AGENTS

When generating code for this repository, the AI Agent must follow these principles:

1. **SOLID Principles & Layered Architecture:**

   * **Java/Spring Boot:** `Controller` $\rightarrow$ `Service` (Interface + Impl) $\rightarrow$ `Repository` $\rightarrow$ `Entity` / `DTO`.

   * **Strict Prohibition:** NO business logic inside Controllers. NO exposing JPA entities directly in REST API responses (always map to DTOs).

2. **Global Exception Handling:** Every Spring Boot service must feature a `@ControllerAdvice` class returning structured error JSON (`timestamp`, `status`, `message`, `details`).

3. **Validation & Logging:** Use `jakarta.validation` (`@NotNull`, `@Positive`) on DTOs. Use `SLF4J` logging instead of `System.out.println`.

4. **Single Module Focus:** When instructed to build a feature, generate code **only** for the requested file or service module. Do not refactor or modify unrelated modules.

5. **Zero Hardcoded Configuration:** Use `application.yml` or `.env` files for database URLs, ports, RabbitMQ hostnames, and API keys.

## 8. STEP-BY-STEP EXECUTION ROADMAP

Follow this exact order when asking the AI coding agent to implement tasks:

* \[ \] **Step 1:** Generate `docker-compose.yml` for local infrastructure (PostgreSQL 15, Redis, RabbitMQ with management UI).

* \[ \] **Step 2:** Scaffold `expense-service` (Spring Boot), configure Flyway migrations, JPA entities, repositories, and DTOs.

* \[ \] **Step 3:** Scaffold `ai-agent-service` (FastAPI), setup `google-genai` integration with Pydantic response models.

* \[ \] **Step 4:** Implement REST communication from `expense-service` to `ai-agent-service` and SMS webhook handler.

* \[ \] **Step 5:** Configure RabbitMQ producer in `expense-service` and listener in `notification-service`.

* \[ \] **Step 6:** Scaffold `api-gateway` (Spring Cloud Gateway) routes and CORS setup.

* \[ \] **Step 7:** Build React SPA frontend components (Dashboard, Input Form, Transaction Table, Budget Gauges).

* \[ \] **Step 8:** Write JUnit 5 unit tests for backend services and pytest for FastAPI.

* \[ \] **Step 9:** Configure GitHub Actions CI/CD workflow and Dockerfiles for deployment.