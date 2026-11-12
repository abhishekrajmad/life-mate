# 07. PROJECT TASK BREAKDOWN

This document outlines the step-by-step task execution roadmap for building the **Smart Life Assistant (SLA)** platform.

1. **Project Documentation Suite**
   Setup the repository and author all mandatory ADF v1.0 documentation files under `docs/` covering project context, requirements, architecture, database design, API contracts, coding standards, testing strategies, and deployment plans.

2. **Local Infrastructure Configuration**
   Configure `docker/docker-compose.yml` to spin up local instances of PostgreSQL 15 (relational database), Redis (gateway cache & rate limiter), and RabbitMQ with management UI enabled (message broker).

3. **Core Expense Service & Database Migrations**
   Scaffold the Java 17 / Spring Boot 3 `expense-service` application and write versioned Flyway SQL scripts (`V1__init_expense_schema.sql`) to create PostgreSQL tables for accounts, categories, and transactions with seed data.

4. **Expense Domain Entities, DTOs & CRUD APIs**
   Implement JPA entities, repositories, DTO classes, service layer logic, and REST controllers for managing financial accounts, transaction ledgers, and monthly category budget limits.

5. **AI Agent Microservice (FastAPI & Gemini API)**
   Scaffold the Python 3.11 FastAPI `ai-agent-service` and integrate the `google-genai` SDK to convert unstructured natural language input or SMS text into structured JSON DTOs containing amount, currency, category, and merchant.

6. **Synchronous AI Integration**
   Connect `expense-service` to `ai-agent-service` using Spring `RestClient` / `RestTemplate` to automatically send user text inputs to the AI service and persist the returned structured DTO in PostgreSQL.

7. **Automated Bank SMS Webhook Endpoint**
   Build a dedicated webhook endpoint (`POST /api/v1/expenses/webhook/sms`) in `expense-service` to receive raw bank transaction SMS text payloads triggered from mobile automation apps (MacroDroid / Tasker) or cURL commands.

8. **Asynchronous Event Pipeline with RabbitMQ**
   Configure a RabbitMQ event producer in `expense-service` to emit an `ExpenseCreatedEvent` upon transaction save, and build a consumer listener in `notification-service` to evaluate monthly spending against budget caps and log threshold alerts.

9. **API Gateway & Routing Configuration**
   Scaffold the `api-gateway` using Spring Cloud Gateway to centralize entry routes (`/api/v1/expenses/**` and `/api/v1/ai/**`), configure global CORS rules for React, and setup Redis rate limiting filters.

10. **React SPA Frontend - Foundation & Input Component**
    Scaffold the React.js frontend application with Tailwind CSS layout styling, state management, and a prominent natural language text input box for typing manual expense entries.

11. **React SPA Frontend - Ledger Table & Budget Gauges**
    Build the transaction history table and progress gauges for category budget limits, ensuring all components explicitly implement the 4 mandatory UI states (Loading, Error, Empty, Data).

12. **End-to-End System Integration**
    Connect the React frontend to the API Gateway and verify the complete flow end-to-end (Text input $\rightarrow$ FastAPI AI parse $\rightarrow$ PostgreSQL save $\rightarrow$ RabbitMQ event emission $\rightarrow$ Notification alert trigger).

13. **Unit Testing & Postman API Collection**
    Write unit test suites using JUnit 5 and Mockito for Spring Boot services and `pytest` for the FastAPI service, then export a complete Postman API test collection.

14. **Containerization & CI/CD Pipeline**
    Write multi-stage `Dockerfile` configurations for each microservice and create the GitHub Actions workflow script (`.github/workflows/deploy.yml`) to automate builds, testing, and Docker image packaging.

15. **Production Cloud Deployment**
    Deploy the React frontend to Vercel, backend microservices to Render/Koyeb, PostgreSQL to Neon.tech, and RabbitMQ to CloudAMQP, followed by final deployment URL testing and video demo recording.