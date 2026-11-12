# 02. REQUIREMENTS ENGINEERING

## 1. User Roles
* **Standard User:** Logs income and expenses, sets category budgets, views dashboard analytics, and receives over-budget alerts.
* **System / Webhook Trigger:** Automated external agent (e.g., MacroDroid/Tasker) pushing SMS payloads to the webhook API.

## 2. Functional Requirements (FR)

### Module 1: AI Natural Language Parsing
* **FR-1.1:** System shall accept raw natural language input strings via manual web input or SMS webhook payloads.
* **FR-1.2:** System shall send unstructured text to the AI Agent Service and receive structured JSON DTOs containing `amount`, `currency`, `category`, and `merchant`.

### Module 2: Core Financial Ledger & Budgets
* **FR-2.1:** System shall maintain accounts with balances and currency definitions in PostgreSQL.
* **FR-2.2:** System shall allow CRUD operations on monthly category budget caps.
* **FR-2.3:** System shall log all transactions with raw input, parsed merchant, category, amount, and creation timestamp.

### Module 3: Asynchronous Budget Alerting
* **FR-3.1:** Expense Service shall emit an `ExpenseCreatedEvent` to RabbitMQ immediately after persisting a transaction.
* **FR-3.2:** Notification Service shall consume `ExpenseCreatedEvent`, evaluate monthly category totals against limits, and emit over-budget alerts if breached.

## 3. Non-Functional Requirements (NFR)
* **NFR-1 (Performance):** Gateway API response time for transaction entry shall be $< 100\text{ms}$ (offloading notification processing asynchronously).
* **NFR-2 (Resilience):** If AI Agent Service is unreachable, Expense Service shall fallback to a manual categorization entry without crashing.
* **NFR-3 (Data Integrity):** PostgreSQL database schema changes must be versioned and managed exclusively via Flyway migration scripts.

## 4. User Stories & Acceptance Criteria

### User Story US-01: Natural Language Expense Logging
> **As an** early-career professional,  
> **I want to** type "Spent 450 on lunch with team" into a single text box,  
> **So that** my expense is auto-categorized and logged without multi-step forms.

* **Acceptance Criteria:**
  1. System extracts amount (`450.00`), category (`Food & Dining`), and merchant (`Team Lunch`).
  2. Transaction is saved to PostgreSQL and displayed on the ledger table instantly.
  3. Budget gauge updates automatically.

### User Story US-02: Bank SMS Webhook Automation
> **As a** user who forgets to log expenses,  
> **I want** bank transaction SMS alerts on my phone to trigger a webhook,  
> **So that** my cash flow is logged passively.

* **Acceptance Criteria:**
  1. `POST /api/v1/expenses/webhook/sms` receives bank SMS string.
  2. Transaction is parsed via AI and persisted seamlessly.

## 5. Out of Scope (Phase 1)
* Real-time bank OAuth2 Account Aggregator integration.
* Payment gateway integrations (Razorpay/Stripe live transactions).
* Multi-tenant corporate accounting.