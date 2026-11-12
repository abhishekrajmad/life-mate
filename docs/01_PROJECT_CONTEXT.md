# 01. PROJECT CONTEXT & OVERVIEW

## 1. Vision & Executive Summary
The **Smart Life Assistant (SLA)** is a distributed, polyglot microservice application designed to eliminate friction in daily personal operations. Starting with personal finance management, SLA accepts unstructured natural language inputs (via manual web entry or automated bank SMS webhooks), uses a dedicated Python AI microservice (Gemini API) to extract structured JSON entities, persists transactions in PostgreSQL, and asynchronously evaluates budget thresholds using RabbitMQ.

## 2. Core Problem Statement
1. **High Data Entry Friction:** Manual multi-screen forms for categorizing expenses lead to high drop-off. Users prefer entering plain text (e.g., *"Spent 450 rupees on biryani"*) or having bank transaction SMS text automatically parsed.
2. **Monolithic & Synchronous Bottlenecks:** Traditional monoliths handle payment parsing, ledger writes, and notification processing synchronously. Slow third-party AI calls or notification tasks block API threads, causing system latency.

## 3. Technology Matrix

| Subsystem / Service | Technology Stack | Storage / Messaging | Primary Function |
| :--- | :--- | :--- | :--- |
| **API Gateway** | Java 17 / Spring Cloud Gateway | Redis *(Rate Limiting & Sessions)* | Single entry point, CORS configuration, rate limiting. |
| **Expense Service** | Java 17 / Spring Boot 3 | PostgreSQL 15 *(Flyway Migrations)* | Core financial ledger, accounts, budget caps, Flyway DB migrations. |
| **AI Agent Service** | Python 3.11 / FastAPI | Stateless *(Gemini API SDK)* | Natural language entity extraction (Amounts, Categories, Merchants). |
| **Notification Service** | Java 17 / Spring Boot 3 | Memory / Log Sink | Asynchronous RabbitMQ listener for budget limit breach alerts. |
| **Fitness Service** *(Phase 2)*| Java 17 / Spring Boot 3 | MongoDB Atlas | Document store for flexible health logs (Steps, Calories, Macros). |
| **Frontend Web App** | React.js 18 / Tailwind CSS | Browser Storage | SPA Dashboard, Natural Language Input Box, Expense Ledger, Progress Gauges. |

## 4. Master Folder Structure

```text
smart-life-assistant/
├── docs/                               # ADF v1.0 Documentation Suite
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
│   └── PROJECT_INIT_PROMPT.md          # AI Master Context Prompt
├── backend/
│   ├── api-gateway/                    # Spring Cloud Gateway
│   ├── expense-service/                # Core Ledger & Budget Service
│   ├── ai-agent-service/               # Python FastAPI NLP Service
│   └── notification-service/           # RabbitMQ Event Consumer
├── frontend/                           # React.js SPA
├── docker/                             # Dockerfiles & docker-compose.yml
├── postman/                            # Exported API Collections
└── .github/                            # GitHub Actions CI/CD Workflows
```

## 5. Git Branching Strategy
* **`main`**: Production-ready, fully tested code only.
* **`develop`**: Integration branch for completed feature tasks.
* **`feature/<name>`**: Individual developer feature branches (e.g., `feature/expense-crud`, `feature/fastapi-gemini`, `feature/rabbitmq-consumer`).